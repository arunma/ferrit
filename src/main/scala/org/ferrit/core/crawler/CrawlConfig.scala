package org.ferrit.core.crawler

import scala.util.{Failure, Success, Try}
import org.ferrit.core.filter.UriFilter
import org.ferrit.core.uri.CrawlUri
import org.ferrit.core.crawler.CrawlConfig._

case class CrawlConfig (

  /**
   * The unique identifier of the crawler configuration.
   */
  id: String,

  /**
   * The User-Agent header sent to web servers during a fetch to identify
   * the crawler. This must set this before starting a crawl. If not set,
   * the default user-agent property in application.conf is used instead.
   */
  userAgent: Option[String],
  
  /**
   * The seeds are the starting points of a crawl and should
   * be ordinary URLs not regex patterns. Additional 
   * follow patterns are needed to accept the seeds for following.
   * Adding seeds by themselves are not enough.
   */
  seeds: Seq[CrawlUri],

  /**
   * Configuration for the UriFilter used by the crawler.
   */
  uriFilter: UriFilter,

  /**
   * One or more test cases to validate the UriFilter rules.
   */
  tests: Option[Seq[String]],

  /**
   * The name of the crawl used for identification in crawl logs and job monitoring tools.
   * For example, the name of the website being crawled or meaningful group
   * name when crawling multiple sites.
   */
  crawlerName: String,
  
  /**
   * A custom politeness setting to use if the 'Crawl-delay' directive is
   * not found in the robots.txt file.
   */
  crawlDelayMillis: Int,
  
  /**
   * The length of time a crawl can run before quitting.
   */
  crawlTimeoutMillis: Long,

  /**
   * Set the maximum depth of the crawl, which is to say the number of
   * links away from the seed URIs. A depth of 0 means that only the 
   * seed URIs will be crawled.
   */
  maxDepth: Int,

  /**
   * The maximum number of fetches that the crawler can make during the crawl. 
   */
  maxFetches: Int,
  
  /**
   * The maximum number of URLs that can be queued for fetching.
   * The crawler will quit if the number of queued URLs exceeds this value.
   * This is to prevent out-of-memory exceptions and alert you about larger than
   * expected queue sizes. 
   */
  maxQueueSize: Int,
  
  /**
   * The % percentage of request fails tolerated before the crawler gives up.
   * This prevents misconfigured crawlers from running on indefinitely.
   * Too many 404 errors could indicate that the seed URIs are incorrect.
   * Too many 500 errors could indicate an over-eager crawler or a server
   * trying to ban the crawler.
   */
  maxRequestFails: Double

) {
  
  def getUserAgent:String = userAgent.getOrElse("")
  
  def validated: Try[Boolean] = {
    def because(reason: String) = CrawlRejectException(reason)

    if (getUserAgent.trim.isEmpty)
      Failure(because(UserAgentMissing))
    else if (seeds == null || seeds.isEmpty)
      Failure(because(SeedsAreMissing))
    else if (!seeds.forall(uriFilter.accept))
      Failure(because(SeedsAreRejected.format(seeds.map(uriFilter.explain).mkString(" and "))))
    else
      Success(true)
  }
}

object CrawlConfig {
  
  val UserAgentMissing = 
    "Please set a sensible userAgent value in your crawler configuration." + 
    "This is used to form a User-agent header during HTTP fetch requests " +
    "to help identify your crawler to the web server. Some web servers will " +
    "block requests if a user agent is not defined. " + 
    "The user agent is also used when evaluating Robots Exclusion rules."

  val SeedsAreMissing = 
    "seed URIs are required to start a crawler"

  val SeedsAreRejected = 
    "The crawler URI filter rejected the seeds: %s"

}
