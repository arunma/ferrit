package org.ferrit.core.crawler

sealed abstract class CrawlOutcome(val state: String, val message: String)
object KeepCrawling extends CrawlOutcome("Continuing", "Keep crawling")
object CompletedOkay extends CrawlOutcome("Completed", "Completed Okay")
object StopRequested extends CrawlOutcome("Stopped", "Stop requested")
object TooManyQueued extends CrawlOutcome("Aborted", "Too many URIs queued for crawling")
object TooManyFetches extends CrawlOutcome("Aborted", "Crawl exceeded maximum fetch requests")
object TooManyFetchesFailed extends CrawlOutcome("Aborted", "Too many failed fetches")
object CrawlTimeout extends CrawlOutcome("Aborted", "Crawl exceeded maximum crawl time")
case class InternalError(reason: String, t: Throwable) extends CrawlOutcome("Aborted", reason)
