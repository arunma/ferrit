package org.ferrit.dao

import akka.actor.Actor
import org.ferrit.core.crawler.CrawlWorker._
import org.ferrit.core.crawler.FetchMessages._
import org.ferrit.core.model.{CrawlJob, Document, DocumentMetaData, FetchLogEntry}
import org.joda.time.DateTime

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration._

/**
 * This actor receives updates from the CrawlWorker and persists
 * documents, meta data documents and fetch log entries.
 *
 * There are some outstanding issues with this journal:
 * no failure handling, error recovery or ability to apply backpressure
 * to crawl workers in the event that the database cannot keep up with writes.
 */
class Journal(daoFactory: DAOFactory) extends Actor {

  import org.ferrit.dao.Journal.FlushJobState
  val jobDao: CrawlJobDAO = daoFactory.crawlJobDao
  val fleDao: FetchLogEntryDAO = daoFactory.fetchLogEntryDao
  val docMetaDao: DocumentMetaDataDAO = daoFactory.documentMetaDataDao
  val docDao: DocumentDAO = daoFactory.documentDao
  /**
   * Flush job states every N seconds rather than
   * after each fetch too reduce excessive writes.
   */
  val FlushDelay = 10
  private[dao] implicit val execContext = context.system.dispatcher
  var jobStates: Map[String, CrawlJob] = Map.empty

  def receive = {
    case StartOkay(_, job) =>
      jobDao.insertByCrawler(Seq(job))
      jobDao.insertByDate(Seq(job))

    case Stopped(_, job) =>
      jobDao.insertByCrawler(Seq(job))
      jobDao.insertByDate(Seq(job))
      // remove job afterwards to prevent a late update over-writing finish state
      jobStates = jobStates - job.jobId

    case FlushJobState =>
      if (jobStates.nonEmpty) {
        // PERFORMANCE ALERT
        // Frequent updates to these job tables will result
        // in degraded read performance over time
        val jobs = jobStates.values.toSeq
        jobDao.insertByCrawler(jobs)
        jobDao.insertByDate(jobs)
        jobStates = Map.empty // important to reset!
      }

    case FetchResult(statusCode, fetchJob, crawlJob, response, overallDuration, parserResult) =>
      val linksExtracted = parserResult match {
        case Some(pr) => pr.links.size
        case None => 0
      }

      val parseDuration = parserResult match {
        case Some(pr) => pr.duration
        case None => 0
      }

      val now = new DateTime
      val uri = fetchJob.uri
      val contentType = response.contentType.getOrElse("undefined")
      val docMeta = DocumentMetaData(
        crawlJob.crawlerId,
        crawlJob.jobId,
        uri.crawlableUri,
        contentType,
        response.contentLength,
        fetchJob.depth,
        now,
        response.statusCode.toString
      )
      docMetaDao.insert(docMeta)

      val doc = Document(
        crawlJob.crawlerId,
        crawlJob.jobId,
        uri.crawlableUri,
        contentType,
        response.content
      )
      docDao.insert(doc)

      // Will over-write and update job
      jobDao.insertByDate(Seq(crawlJob))

      val fle = FetchLogEntry(
        crawlJob.crawlerId,
        crawlJob.jobId,
        now, 
        uri.crawlableUri,
        fetchJob.depth,
        response.statusCode,
        response.contentType,
        response.contentLength,
        linksExtracted,
        overallDuration.toInt,
        response.stats.timeCompleted.toInt,
        parseDuration.toInt,
        crawlJob.urisSeen,
        crawlJob.urisQueued,
        crawlJob.fetchCounters.getOrElse(FetchSucceeds, 0)
      )
      fleDao.insert(fle)

      // Kick off next flush of job states.
      // By the time it runs there will be job states captured.
      // This depends on jobStates being reset after each flush

      if (jobStates.isEmpty) context.system.scheduler.scheduleOnce(
        FlushDelay.seconds, self, FlushJobState
      )

      jobStates = jobStates + (crawlJob.jobId -> crawlJob)
  }
}

object Journal {
  case object FlushJobState
}