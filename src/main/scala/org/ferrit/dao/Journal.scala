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
  private case object FlushJobState
  private val jobDao: CrawlJobDAO = daoFactory.crawlJobDao
  private implicit val execContext = context.system.dispatcher
  private var jobStates: Map[String, CrawlJob] = Map.empty

  /**
   * Flush job states every N seconds rather than
   * after each fetch too reduce excessive writes.
   */
  private val FlushDelay = 10

  def receive = {
    case StartOkay(_, job) =>
      jobDao.insertByCrawler(Seq(job))
      jobDao.insertByDate(Seq(job))

    case Stopped(_, job) =>
      jobDao.insertByCrawler(Seq(job))
      jobDao.insertByDate(Seq(job))
      // remove job afterwards to prevent a late update over-writing finish state
      jobStates = jobStates - job.jobId

    case FlushJobState if jobStates.nonEmpty =>
      // PERFORMANCE ALERT
      // Frequent updates to these job tables will result
      // in degraded read performance over time
      val jobs = jobStates.values.toSeq
      jobDao.insertByCrawler(jobs)
      jobDao.insertByDate(jobs)
      jobStates = Map.empty // important to reset!

    case FetchResult(statusCode, fetchJob, crawlJob, response, overallDuration, parserResult) =>
      // Will over-write and update job
      jobDao.insertByDate(Seq(crawlJob))
      val now = new DateTime
      val uri = fetchJob.uri.crawlableUri
      val contentType = response.contentType.getOrElse("undefined")

      daoFactory.documentMetaDataDao.insert(DocumentMetaData(
        crawlJob.crawlerId,
        crawlJob.jobId,
        uri,
        contentType,
        response.contentLength,
        fetchJob.depth,
        now,
        response.statusCode.toString
      ))

      daoFactory.documentDao.insert(Document(
        crawlJob.crawlerId,
        crawlJob.jobId,
        uri,
        contentType,
        response.content
      ))

      val (linksExtracted, parseDuration) = parserResult match {
        case Some(pr) => (pr.links.size, pr.duration.toInt)
        case None => (0, 0)
      }

      daoFactory.fetchLogEntryDao.insert(FetchLogEntry(
        crawlJob.crawlerId,
        crawlJob.jobId,
        now,
        uri,
        fetchJob.depth,
        response.statusCode,
        response.contentType,
        response.contentLength,
        linksExtracted,
        overallDuration.toInt,
        response.stats.timeCompleted.toInt,
        parseDuration,
        crawlJob.urisSeen,
        crawlJob.urisQueued,
        crawlJob.fetchCounters.getOrElse(FetchSucceeds, 0)
      ))

      // Kick off next flush of job states.
      // By the time it runs there will be job states captured.
      // This depends on jobStates being reset after each flush
      if (jobStates.isEmpty) context.system.scheduler.scheduleOnce(
        FlushDelay.seconds, self, FlushJobState
      )

      jobStates = jobStates + (crawlJob.jobId -> crawlJob)
  }
}
