package org.ferrit.core.crawler

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, Terminated}
import akka.pattern.{ask, pipe}
import akka.routing.Listen
import akka.util.Timeout
import com.typesafe.config.Config
import org.ferrit.core.crawler.CrawlWorker.{Run, StartFailed, StartOkay, StopCrawl}
import org.ferrit.core.http.HttpClient
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.parser.MultiParser
import org.ferrit.core.uri.{InMemoryFrontier, InMemoryUriCache}

import scala.concurrent.Future
import scala.concurrent.duration._


/**
 * Manages a collection of running crawler jobs up to a given limit.
 */
class SpiderManager(
  node: String,
  userAgent: String,
  maxCrawlers: Int,
  httpClient: HttpClient,
  robotRulesCache: ActorRef
) extends Actor {
  import org.ferrit.core.crawler.SpiderManager._

  private[crawler] implicit val execContext = context.system.dispatcher
  private[crawler] val AskTimeout = new Timeout(1.second)
  private[crawler] var jobs: Map[String, JobEntry] = Map.empty

  // Crawlers should not restart if they crash
  override val supervisorStrategy = OneForOneStrategy(0, 1.second) {
    case _: Exception => Stop
  }

  override def receive = messagesFromClients orElse messagesFromWorkers

  def messagesFromClients: Receive = {
    case JobsQuery() =>
      sender ! JobsInfo(jobs.values.map(_.job).toSeq)

    case StartJob(config, listeners) =>
      if (jobs.size >= maxCrawlers) {
        sender ! JobStartFailed(new CrawlRejectException(TooManyCrawlers))
      } else if (jobs.exists(pair => pair._2.job.crawlerId == config.id)) {
        sender ! JobStartFailed(new CrawlRejectException(CrawlerExists))
      } else {
        val resolvedConfig = config.userAgent match {
          case Some(ua) => config
          case None => config.copy(userAgent = Some(userAgent))
        }
        startCrawlJob(resolvedConfig, listeners) pipeTo sender
      }

    case StopJob(id) =>
      val reply = jobs.get(id) match {
        case None => JobNotFound
        case Some(entry) =>
          entry.crawler ! StopCrawl
          StopAccepted(Seq(entry.job.jobId))
      }
      sender ! reply

    case StopAllJobs() =>
      val ids = jobs.map { pair =>
        val (crawler, job) = (pair._2.crawler, pair._2.job)
        crawler ! StopCrawl
        job.jobId
      }
      sender ! StopAccepted(ids.toSeq)
  }

  /* = = = = = = = = = = =  Implementation  = = = = = = = = = = =  */
  def startCrawlJob(config: CrawlConfig, listeners: Seq[ActorRef]): Future[AnyRef] = {
    val newJob = CrawlJob.create(config, node)
    val crawler = context.actorOf(Props(
      classOf[CrawlWorker],
      newJob,
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      httpClient,
      robotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))

    context.watch(crawler)
    listeners.foreach(l => crawler ! Listen(l))
    jobs = jobs + (newJob.jobId -> JobEntry(crawler, newJob))
    crawler.ask(Run)(AskTimeout).map {
      case StartOkay(msg, job) => newJob
      case StartFailed(t, conf) => JobStartFailed(t)
    }
  }

  def messagesFromWorkers: Receive = {
    case CrawlWorker.Stopped(outcome, job) =>
    case Terminated(child) =>
      jobs.find(_._2.crawler == child) match {
        case Some(pair) => jobs = jobs - pair._1
        case None =>
      }
  }

  private[crawler] case class JobEntry(crawler: ActorRef, job: CrawlJob)
}

object SpiderManager {
  val TooManyCrawlers = "The maximum number of active crawlers is reached"
  val CrawlerExists = "There is already an active crawler with same crawler configuration"

  def props(config: Config, spiderClient: HttpClient, robotsRuleCache: ActorRef): Props = {
    Props(classOf[SpiderManager],
      config.getString("app.server.host"),
      config.getString("app.crawler.user-agent"),
      config.getInt("app.crawler.max-crawlers"),
      spiderClient,
      robotsRuleCache)
  }

  case class StartJob(config: CrawlConfig, crawlListeners: Seq[ActorRef])

  case class JobStartFailed(t: Throwable)

  case class StopJob(id: String)

  case class StopAllJobs()

  case class StopAccepted(ids: Seq[String])

  case class JobsQuery()

  case class JobsInfo(jobs: Seq[CrawlJob])

  case object JobNotFound
}