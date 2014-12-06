package org.ferrit.core.model

import org.joda.time.DateTime

/** A single entry that captures various properties of a single fetch operation
  * to help monitor progress and performance.
  * Also includes some additional crawl stats.
  */
case class FetchLogEntry(

  // key for the entry to co-locate it with other entries
  crawlerId: String,

  // the id of the CrawlJob associated with this fetch
  jobId: String,

  // FETCH STATS
  // -----------

  // identifies when the fetch happened
  logTime: DateTime,

  // the URI that was crawled
  uri: String,

  // the depth-at-the-time of the URI within a crawl
  uriDepth: Int,

  // the HTTP response status code returned from the server
  statusCode: Int,

  // the content-type header returned from the server
  contentType: Option[String],

  // the length of the asset
  contentLength: Int,

  // many links may explain slow parsing
  linksExtracted: Int,

  // overall duration of fetch (request and parse)
  fetchDuration: Int,

  // performance of the HTTP client
  requestDuration: Int,

  // performance of the content parser
  parseDuration: Int,

  // CRAWL STATS (RUNNING TOTALS)
  // ----------------------------

  // allows cached URI monitoring
  urisSeen: Int,

  // allows queue size monitoring
  urisQueued: Int,

  // total fetches in this crawl so far
  fetches: Int)