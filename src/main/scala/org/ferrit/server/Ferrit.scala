package org.ferrit.server

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import org.ferrit.core.crawler.SpiderManager.{JobNotFound, JobsInfo, JobsQuery, StartJob, StopAccepted, StopAllJobs, StopJob}
import org.ferrit.core.crawler.{CrawlConfig, CrawlConfigTester, CrawlRejectException}
import org.ferrit.core.json.PlayJsonImplicits._
import org.ferrit.core.model.{CrawlJob, Crawler}
import org.ferrit.server.PlayJsonImplicits._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import spray.http.{StatusCode, StatusCodes}
import spray.httpx.PlayJsonSupport._
import spray.routing.{Directive1, _}
import spray.util.LoggingContext

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

case class BadParamRejection(name: String, value: String) extends Rejection

case class BadEntityRejection(name: String, value: String) extends Rejection

object Ferrit extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("ferrit-spider")
  implicit val customRejectionHandler = RejectionHandler {
    case MalformedRequestContentRejection(msg, cause) :: _ =>
      complete {
        val sc = StatusCodes.BadRequest
        sc -> ErrorMessage(sc.intValue, "The request entity could not be unmarshalled.")
      }

    case BadEntityRejection(name, value) :: _ =>
      complete {
        val sc = StatusCodes.NotFound
        sc -> ErrorMessage(sc.intValue, s"No [$name] found with identifier [$value]")
      }

    case BadParamRejection(name, value) :: _ =>
      complete {
        val sc = StatusCodes.BadRequest
        sc -> ErrorMessage(sc.intValue, "Parameter [$name] has invalid value [$value]")
      }
  }

  startServer(
    interface = config.getString("app.server.host"),
    port = config.getInt("app.server.port")) {
    routes
  }
  val spiderContext = new ProdSpiderContext
  val config = spiderContext.config
  val spiderManager = spiderContext.spiderManager
  val daoFactory = spiderContext.daoFactory
  val crawlJobDao = daoFactory.crawlJobDao
  val crawlerDao = daoFactory.crawlerDao
  val routes =
    path("crawlers" / Segment / "jobs" / Segment / "fetches") { (crawlerId, jobId) =>
      get {
        withCrawlJob(crawlerId, jobId)(_ => complete(daoFactory.fetchLogEntryDao.find(jobId)))
      }
    } ~
        path("crawlers" / Segment / "jobs" / Segment / "assets") { (crawlerId, jobId) =>
          get {
            withCrawlJob(crawlerId, jobId)(_ => complete(daoFactory.documentMetaDataDao.find(jobId)))
          }
        } ~
        path("crawlers" / Segment / "jobs" / Segment) { (crawlerId, jobId) =>
          get {
            withCrawlJob(crawlerId, jobId)(job => complete(job))
          }
        } ~
        path("crawlers" / Segment / "jobs") { crawlerId =>
          get {
            withCrawler(crawlerId) { _ => complete(crawlJobDao.find(crawlerId))}
          }
        } ~
        path("crawlers" / Segment) { crawlerId =>
          get {
            withCrawler(crawlerId)(crawler => complete(crawler.config))
          } ~
              put {
                entity(as[CrawlConfig]) { config =>
                  withCrawler(crawlerId) { _ =>
                    complete {
                      CrawlConfigTester.testConfig(config) match {
                        case results if results.allPassed =>
                          val config2 = config.copy(id = crawlerId)
                          crawlerDao.insert(Crawler(crawlerId, config2))
                          StatusCodes.Created -> config2
                        case someFailed => StatusCodes.BadRequest -> someFailed
                      }
                    }
                  }
                }
              } ~
              delete {
                withCrawler(crawlerId) { _ =>
                  complete {
                    crawlerDao.delete(crawlerId)
                    StatusCodes.NoContent
                  }
                }
              }
        } ~
        path("crawlers") {
          get {
            complete(crawlerDao.findAll().map(crawler => crawler.config))
          } ~
              post {
                entity(as[CrawlConfig]) { config =>
                  complete {
                    CrawlConfigTester.testConfig(config) match {
                      case results if results.allPassed =>
                        val crawler = Crawler.create(config)
                        crawlerDao.insert(crawler)
                        StatusCodes.Created -> crawler.config
                      case someFailed => StatusCodes.BadRequest -> someFailed
                    }
                  }
                }
              }
        } ~
        path("crawl-config-test") {
          post {
            entity(as[CrawlConfig]) { config =>
              complete {
                val results = CrawlConfigTester.testConfig(config)
                val sc = if (results.allPassed) StatusCodes.OK else StatusCodes.BadRequest
                sc -> results
              }
            }
          }
        } ~
        path("jobs") {
          get {
            parameter("date" ? DateParamDefault) { dateParam =>
              try {
                val dateKey =
                  if (DateParamDefault == dateParam) new DateTime
                  else DateTimeFormat.forPattern(DateParamFormat).parseDateTime(dateParam)
                complete(crawlJobDao.find(dateKey.withTimeAtStartOfDay))
              } catch {
                case e: IllegalArgumentException => reject(BadParamRejection("date", dateParam))
              }
            }
          }
        } ~
        path("job-processes") {
          post {
            entity(as[Id]) { crawlerId =>
              withCrawler(crawlerId.id) { crawler =>
                complete {
                  spiderManager
                      .ask(StartJob(crawler.config, Seq(spiderContext.logger, spiderContext.journal)))(startJobTimeout)
                      .mapTo[CrawlJob]
                      .map({ job => StatusCodes.Created -> job})
                }
              }
            }
          } ~
              get {
                complete {
                  spiderManager
                      .ask(JobsQuery())(askTimeout)
                      .mapTo[JobsInfo]
                      .map({ jobsInfo => jobsInfo.jobs})
                }
              } ~
              delete {
                complete {
                  spiderManager
                      .ask(StopAllJobs())(askTimeout)
                      .mapTo[StopAccepted]
                      .map(sa => StatusCodes.Accepted -> Message(StopAllJobsAcceptedMsg.format(sa.ids.size)))
                }
              }

        } ~
        path("job-processes" / Segment) { jobId =>
          delete {
            onSuccess((spiderManager ? StopJob(jobId))(askTimeout)) {
              case StopAccepted(Seq(id)) => complete(StatusCodes.Accepted -> Message(StopJobAcceptedMsg.format(id)))
              case JobNotFound => reject(BadEntityRejection("crawl job", jobId))
            }
          }
        }
  val askTimeout = new Timeout(3.seconds)
  val startJobTimeout = new Timeout(30.seconds)
  val DateParamDefault = "no-date"
  val DateParamFormat = "YYYY-MM-dd"
  val NoPostToNamedCrawlerMsg = "Cannot post to an existing crawler resource"
  val StopJobAcceptedMsg = "Stop request accepted for job [%s]"
  val StopAllJobsAcceptedMsg = "Stop request accepted for %s jobs"
  val ShutdownReceivedMsg = "Shutdown request received"

  implicit def executionContext: ExecutionContextExecutor = actorRefFactory.dispatcher

  implicit def customExceptionHandler(implicit log: LoggingContext) = ExceptionHandler {
    case throwable: Throwable =>
      requestInstance { request =>
        complete {
          val ServerErrorMsg = "Apologies, an internal server error occurred whilst handling your request"
          val (sc: StatusCode, msg: String) = throwable match {
            case cre: CrawlRejectException =>
              StatusCodes.InternalServerError -> ServerErrorMsg

            case other =>
              StatusCodes.InternalServerError -> ServerErrorMsg
          }

          log.error(throwable, s"Request exception for $request: $msg")
          sc -> ErrorMessage(sc.intValue, msg)
        }
      }
  }

  def withCrawler(crawlerId: String): Directive1[Crawler] = {
    crawlerDao.find(crawlerId) match {
      case None => reject(BadEntityRejection("crawler", crawlerId))
      case Some(crawler) => provide(crawler)
    }
  }

  def withCrawlJob(crawlerId: String, crawlJobId: String): Directive1[CrawlJob] = {
    crawlJobDao.find(crawlerId, crawlJobId) match {
      case None => reject(BadEntityRejection("crawl job", crawlJobId))
      case Some(crawlJob) => provide(crawlJob)
    }
  }
}

