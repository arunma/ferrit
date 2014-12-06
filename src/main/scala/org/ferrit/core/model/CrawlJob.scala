package org.ferrit.core.model

import org.ferrit.core.crawler.CrawlConfig
import org.ferrit.core.util.{ Media, UniqueId }
import org.joda.time.DateTime

sealed case class CrawlJob(
    crawlerId: String,
    crawlerName: String,
    jobId: String,
    node: String,
    partitionDate: DateTime,
    snapshotDate: DateTime,
    createdDate: DateTime,
    finishedDate: Option[DateTime],
    duration: Long,
    outcome: Option[String],
    message: Option[String],
    urisSeen: Int,
    urisQueued: Int,
    fetchCounters: Map[String, Int],
    responseCounters: Map[String, Int],
    mediaCounters: Map[String, Media]) {

  def isFinished: Boolean = finishedDate.nonEmpty
}

object CrawlJob {
  def create(config: CrawlConfig, node: String) = CrawlJob(
    crawlerId = config.id,
    crawlerName = config.crawlerName,
    jobId = UniqueId.next,
    node = node,
    partitionDate = new DateTime().withTimeAtStartOfDay,
    snapshotDate = new DateTime,
    createdDate = new DateTime,
    finishedDate = None,
    duration = 0,
    outcome = None,
    message = None,
    urisSeen = 0,
    urisQueued = 0,
    fetchCounters = Map.empty,
    responseCounters = Map.empty,
    mediaCounters = Map.empty)
}
