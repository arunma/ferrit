package org.ferrit.core.crawler

import akka.actor.{ Actor, ActorRef }
import akka.event.Logging
import akka.pattern.{ ask, pipe }
import akka.routing.Listeners
import akka.util.Timeout
import org.ferrit.core.crawler.FetchMessages._
import org.ferrit.core.http.{ DefaultResponse, Get, HttpClient, Response, Stats }
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.parser.{ ContentParser, ParserResult }
import org.ferrit.core.robot.RobotRulesCacheActor.{ Allow, DelayFor }
import org.ferrit.core.uri.{ CrawlUri, FetchJob, Frontier, UriCache }
import org.ferrit.core.util.{ Counters, MediaCounters, Stopwatch }
import org.joda.time.{ DateTime, Duration }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/** This has become a big ball of mud that needs splitting up.
  * Has too many responsibilities at the moment.
  */
class CrawlWorker(
    job: CrawlJob,
    config: CrawlConfig,
    frontier: Frontier,
    uriCache: UriCache,
    httpClient: HttpClient,
    robotRulesCache: ActorRef,
    contentParser: ContentParser,
    stopRule: StopRule) extends Actor with Listeners {

  import org.ferrit.core.crawler.CrawlWorker._

  private[crawler] implicit val execContext = context.system.dispatcher
  private[crawler] val Log = Logging(context.system, getClass)
  private[crawler] val RobotRequestTimeout = new Timeout(20.seconds)
  private[crawler] val SupportedSchemes = Set("http", "https")
  private[crawler] val Started = new DateTime
  private[crawler] var fetchCounters = Counters()
  private[crawler] var responseCodes = Counters()
  private[crawler] var mediaCounters = MediaCounters()
  private[crawler] var state = CrawlStatus(crawlStop = new DateTime().plus(config.crawlTimeoutMillis))

  override def receive = listenerManagement orElse {
    case Run => initCrawler.pipeTo(sender).map {
      case reply @ StartOkay(_, _) =>
        context.become(crawlRunning)
        gossip(reply)
        self ! NextDequeue
      case reply @ StartFailed(_, _) =>
        stopWith(reply)
    }
  }

  def crawlRunning: Receive = listenerManagement orElse {
    case NextDequeue =>
      stopRule.ask(config, state, fetchCounters, frontier.size) match {
        case KeepCrawling => scheduleNext
        case otherOutcome => stopWith(Stopped(otherOutcome, completeJob(otherOutcome, None)))
      }

    case NextFetch(fe) if (state.alive) => fetchNext(fe)
    case StopCrawl => state = state.stop // Stopping not immediate if in a fetch
  }

  private def initCrawler = config.validated match {
    case Failure(t) => Future.successful(StartFailed(t, config))
    case Success(b) =>
      enqueueFetchJobs(config.seeds.map(s => FetchJob(s, 0)).toSet)
        .map(_ => StartOkay("Started okay", job))
        .recover({ case t => StartFailed(t, config) })
  }

  private def scheduleNext = frontier.dequeue match {
    case Some(f: FetchJob) =>
      getFetchDelayFor(f.uri) map { delay =>
        gossip(FetchScheduled(f, delay))
        context.system.scheduler.scheduleOnce(delay.milliseconds, self, NextFetch(f))
      }

    case None => // empty frontier, code smell to fix
  }

  /** Must batch enqueue FetchJob so that async fetch decisions about
    * all the FetchJob are made BEFORE trying to access Frontier and UriCache
    * to prevent a race condition when accessing the Frontier.
    */
  private def enqueueFetchJobs(fetchJobs: Set[FetchJob]) = {
    val future = Future.sequence(fetchJobs.map({ f => isFetchable(f) }))
    future.recoverWith({ case t => Future.failed(t) })
    future.map(
      _.map({ pair =>
        val (f, d) = pair
        dgossip(FetchDecision(f.uri, d))
        if (OkayToFetch == d) {
          frontier.enqueue(f) // only modify AFTER async fetch checks
          uriCache.put(f.uri) // mark URI as seen
          dgossip(FetchQueued(f))
        }
      }))
  }

  private def stopWith(msg: Any) = {
    state = state.dead
    gossip(msg)
    context.stop(self)
  }

  private def stopWithFailure(t: Throwable) = {
    val outcome = InternalError("Crawler failed to complete: " + t.getLocalizedMessage, t)
    stopWith(Stopped(outcome, completeJob(outcome, Some(t))))
  }

  private def completeJob(outcome: CrawlOutcome, throwOpt: Option[Throwable]) = {
    val finished = new DateTime
    val message = throwOpt match {
      case Some(t) => outcome.message + ": " + t.getLocalizedMessage
      case None => outcome.message
    }

    job.copy(
      snapshotDate = new DateTime,
      finishedDate = Some(finished),
      duration = new Duration(Started, finished).getMillis,
      outcome = Some(outcome.state),
      message = Some(message),
      urisSeen = uriCache.size,
      urisQueued = frontier.size,
      fetchCounters = fetchCounters.counters,
      responseCounters = responseCodes.counters,
      mediaCounters = mediaCounters.counters)
  }

  // Must check robot rules after UriFilter test
  // to avoid unnecessary downloading of robots.txt files for
  // sites that will never be visited anyway and robot fetch fails
  // on unsupported schemes like mailto/ftp.
  private def isFetchable(f: FetchJob) = try {
    val uri = f.uri
    if (uriCache.contains(uri))
      Future.successful((f, SeenAlready))
    else if (!SupportedSchemes.contains(uri.reader.scheme))
      Future.successful((f, UnsupportedScheme))
    else if (!config.uriFilter.accept(uri))
      Future.successful((f, UriFilterRejected))
    else robotRulesCache
      .ask(Allow(config.getUserAgent, uri.reader))(RobotRequestTimeout)
      .mapTo[Boolean]
      .map(ok => (f, if (ok) OkayToFetch else RobotsExcluded))
  } catch { case t: Throwable => Future.failed(t) }

  private def fetchNext(f: FetchJob) = {
    def doNext = self ! NextDequeue

    val stopwatch = new Stopwatch
    val result = for {
      response <- fetch(f)
      parseResult = parseResponse(response)
    } yield {
      emitFetchResult(f, response, parseResult, stopwatch.duration)
      parseResult match {
        case None => doNext
        case Some(parserResult) =>
          if (f.depth >= config.maxDepth) {
            dgossip(DepthLimit(f))
            doNext
          } else {
            var uris = Set.empty[CrawlUri]
            parserResult.links.foreach(l => l.crawlUri match {
              case Some(uri) => uris = uris + uri
              case _ if l.failMessage.nonEmpty =>
                Log.error(s"URI parse fail: ($l.failMessage.get)")
            })

            // must enqueue BEFORE next fetch
            enqueueFetchJobs(uris.map(FetchJob(_, f.depth + 1)))
              .map({ _ => doNext })
              .recover({ case t => stopWithFailure(t) })
          }
      }
    }

    result.recover({
      case t =>
        gossip(FetchError(f.uri, t))
        doNext
    })
  }

  private def fetch(f: FetchJob) = {
    val uri = f.uri
    val request = Get(config.getUserAgent, uri)

    def onRequestFail(t: Throwable) = {
      // Handle internal error (as distinct from HTTP request error
      // on target server. Synthesize Response so that crawl can continue.
      Log.error(t, "Request failed, reason: " + t.getLocalizedMessage)
      gossip(FetchError(uri, t))
      Future.successful( // technically an async success
        DefaultResponse(
          -1, // internal error code
          Map.empty,
          Array.empty[Byte], // t.getMessage.getBytes,
          Stats.empty,
          request))
    }

    fetchCounters = fetchCounters.increment(FetchAttempts)
    dgossip(FetchGo(f))
    httpClient.request(request).map({ response =>
      dgossip(FetchResponse(uri, response.statusCode))
      response
    }).recoverWith({ case t => onRequestFail(t) })
  }

  private def emitFetchResult(
    fetchJob: FetchJob,
    response: Response,
    result: Option[ParserResult],
    duration: Long) = {
    gossip(FetchResult(
      response.statusCode,
      fetchJob,
      job.copy(
        snapshotDate = new DateTime,
        duration = new Duration(Started, new DateTime).getMillis,
        urisSeen = uriCache.size,
        urisQueued = frontier.size,
        fetchCounters = fetchCounters.counters,
        responseCounters = responseCodes.counters,
        mediaCounters = mediaCounters.counters),
      response,
      duration, // Represents combined fetch + parse not including enqueue time
      result))
  }

  private def parseResponse(response: Response) = {
    responseCodes = responseCodes.increment(response.statusCode.toString)
    response.statusCode match {
      case code if code >= 200 && code <= 299 =>
        fetchCounters = fetchCounters.increment(FetchSucceeds)
        mediaCounters = mediaCounters.add(
          response.contentType.getOrElse("undefined"), 1, response.contentLength)
        if (!contentParser.canParse(response)) None
        else Some(contentParser.parse(response))

      case code if code >= 300 && code <= 399 =>
        fetchCounters = fetchCounters.increment(FetchRedirects)
        None

      case other_codes =>
        fetchCounters = fetchCounters.increment(FetchFails)
        None
    }
  }

  /** Compute the politeness delay before the next fetch.
    * Two possible delay values need considering:
    *
    * 1. A crawl-delay directive in robots.txt file, if it exists
    * 2. The default delay in the CrawlConfig
    *
    * If both values are available then the longest is chosen.
    */
  private def getFetchDelayFor(uri: CrawlUri) = {
    val defDelay = config.crawlDelayMillis
    robotRulesCache
      .ask(DelayFor(config.getUserAgent, uri.reader))(RobotRequestTimeout)
      .mapTo[Option[Int]]
      .map {
        case Some(rulesDelay) => Math.max(rulesDelay, defDelay)
        case None => defDelay
      }
  }

  /** Uncomment to enable additional gossip for debugging,
    * but realise that this will generate a considerably larger
    * number of Actor messages as a consequence.
    */
  private def dgossip(msg: Any) = {} //gossip(msg)
}

object CrawlWorker {
  val FetchAttempts = "FetchAttempts"
  val FetchSucceeds = "FetchSucceeds"
  val FetchFails = "FetchFails"
  val FetchRedirects = "Redirects"

  sealed abstract class Started

  case class StartOkay(msg: String, job: CrawlJob) extends Started()

  case class StartFailed(t: Throwable, config: CrawlConfig) extends Started()

  case class Stopped(outcome: CrawlOutcome, job: CrawlJob)

  // Public messages
  case object Run

  case object StopCrawl

  case object EmptyFrontier

  // Internal messages
  private[crawler] case object NextDequeue

  private[crawler] case class NextFetch(f: FetchJob)

}