package org.ferrit.core.crawler

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, Terminated}
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.routing.Listen
import akka.util.Timeout
import com.typesafe.config.Config
import org.ferrit.core.crawler.CrawlWorker.{Run, StartFailed, StartOkay, StopCrawl}
import org.ferrit.core.http.{NingAsyncHttpClient, HttpClient}
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
  
  
  import SpiderManager._

  private [crawler] implicit val execContext = context.system.dispatcher
  private [crawler] val log = Logging(context.system, getClass)
  private [crawler] val askTimeout = new Timeout(1.second)
  private [crawler] case class JobEntry(crawler: ActorRef, job: CrawlJob)
  private [crawler] var jobs: Map[String, JobEntry] = Map.empty

  // Crawlers should not restart if they crash
  override val supervisorStrategy = OneForOneStrategy(0, 1.second) {
    case _: Exception => Stop
  }

  override def receive = messagesFromClients orElse messagesFromCrawlers


  def messagesFromClients: Receive = {

    case JobsQuery() => 
      sender ! JobsInfo(jobs.values.map(_.job).toSeq)

    case StartJob(config, listeners) =>
      if (jobs.size >= maxCrawlers) {
        sender ! JobStartFailed(new CrawlRejectException(tooManyCrawlers))
      } else if (jobs.exists(pair => pair._2.job.crawlerId == config.id)) {
        sender ! JobStartFailed(new CrawlRejectException(crawlerExists))
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
      val ids:Seq[String] = jobs.map({pair => 
          val crawler = pair._2.crawler
          val job = pair._2.job
          crawler ! StopCrawl
          job.jobId
      }).toSeq
      sender ! StopAccepted(ids)

  }

  def messagesFromCrawlers: Receive = {

    case CrawlWorker.Stopped(outcome, job) =>
    case Terminated(child) => removeJob(child)

  }


  /* = = = = = = = = = = =  Implementation  = = = = = = = = = = =  */

  def startCrawlJob(config: CrawlConfig, listeners: Seq[ActorRef]):Future[AnyRef] = {
    
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
    
    crawler
      .ask(Run)(askTimeout)
      .map({
        case StartOkay(msg, job) => newJob
        case StartFailed(t, config) => JobStartFailed(t)
      })
  }

  def removeJob(child: ActorRef):Unit = {
    jobs.find(_._2.crawler == child) match {
      case Some(pair) =>
        val id = pair._1
        jobs = jobs - id
      case None =>
    }
  }

}

object SpiderManager {
  def props(config: Config, spiderClient: NingAsyncHttpClient, robotsRuleCache: ActorRef): Props = {
    Props(classOf[SpiderManager],
      config.getString("app.server.host"),
      config.getString("app.crawler.user-agent"),
      config.getString("app.crawler.max-crawlers"),
      spiderClient,
      robotsRuleCache)
  }


  case class StartJob(config: CrawlConfig, crawlListeners: Seq[ActorRef])
  case class JobStartFailed(t: Throwable)

  case class StopJob(id: String)
  case class StopAllJobs()
  case class StopAccepted(ids: Seq[String]) // is not a guarantee that job stopped
  case object JobNotFound

  case class JobsQuery()
  case class JobsInfo(jobs: Seq[CrawlJob])

  val tooManyCrawlers = "The maximum number of active crawlers is reached"
  val crawlerExists = "There is already an active crawler with same crawler configuration"

}