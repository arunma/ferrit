package org.ferrit.core.crawler

import org.ferrit.core.parser.ParserResult
import org.ferrit.core.uri.{CrawlUri, FetchJob}
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.http.Response


object FetchMessages {
  // Messages about decision to fetch or not
  sealed abstract class CanFetch {
    override def toString = this.getClass.getSimpleName.replaceAll("\\$","")
  }

  object SeenAlready extends CanFetch
  object OkayToFetch extends CanFetch
  object UriFilterRejected extends CanFetch
  object RobotsExcluded extends CanFetch
  object UnsupportedScheme extends CanFetch

  case class FetchDecision(uri: CrawlUri, decision: CanFetch)
  case class FetchQueued(f: FetchJob)
  case class FetchScheduled(f: FetchJob, delayMs: Long)
  case class FetchGo(f: FetchJob)
  case class FetchResponse(uri: CrawlUri, statusCode:Int)
  case class FetchError(uri: CrawlUri, t: Throwable)

  /**
   * A FetchResult is the primary deliverable of a fetch operation.
   * The ParserResult is an Option because the fetch result needs to
   * encapsulate a notion of fetch failure or inability to parse.
   */
  case class FetchResult(
    // The HTTP response status code
    // is not the Response itself, to avoid memory leaks by handlers
    // although this could be changed later
    statusCode: Int, 
    fetchJob: FetchJob,
    crawlJob: CrawlJob,
    response: Response,

    // Overall duration of the fetch
    duration: Long,

    // Parser result is Option because it is None if the fetch failed
    parserResult: Option[ParserResult]
  )

  case class DepthLimit(f: FetchJob)
}