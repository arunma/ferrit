package org.ferrit.core.robot

import org.ferrit.core.http.{ Get, HttpClient }
import org.ferrit.core.uri.{ CrawlUri, UriReader }
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

/** An implementation of RobotRulesCache that perfectly illustrates why you
  * should never try writing your own caching code if you want to stay sane.
  * This is hideous, I want my life back!
  *
  * <p>Features:</p>
  *
  * <ul>
  * <li>The cache progressively acquires robots txt files and parses them as required.
  * The passed in HttpClient is used to fetch the robots txt file.</li>
  * <li>Supports multiple user agents but the cache structure is best for
  * when there are fewer user agents.</li>
  * <li>The cache of robot rules is a subset of the robots.txt file that applies
  * just to the user agent in questions. This means that directives for
  * common agents like Googlebot and Yahoo are not stored. The tradeoff is
  * that the robots txt file has to be re-fetched for each user agent
  * used by the crawler, which in practice many never be more than once.</li>
  * <li>A cache entry for a particular website and user agent combination expires
  * after a pre-defined period after which time robots txt would be fetched again.
  * Even the cached record of a lack of robots txt file is evicted.</li>
  * <li>Default expiry of cached entries would be 7 days, but is 1 day because
  * there is no checking of server cache expiry so 1 day is a good compromise.</li>
  * <li>Ideally only one cache created per VM.</li>
  * </ul>
  *
  * <blockquote><em>
  * There are only two hard things in Computer Science:
  * cache invalidation and naming things. -- Phil Karlton
  * </em></blockquote>
  *
  * <p>This cache probably screws up on both counts!</p>
  *
  * <p>Internally the cache stores entries in a map of maps keyed at the
  * highest level by user agent on the likelihood that there may be many
  * crawl jobs running with each using more or less the same agent.
  * A cache entry is an Option set to None when robots.txt was not found.
  * The cache evicts the entry after it expires and re-fetches robots.txt.</p>
  *
  * <p>The cache structure can be visualised like this:</p>
  *
  * <pre>
  *
  * "All agents: *" ->
  * http://site.com -> SiteEntry(Some(RobotRules), expiry)
  * http://site.com:8080 -> SiteEntry(Some(RobotRules), expiry)
  * http://site_with_no_rules.com -> SiteEntry(None, expiry)
  * http://othersite_with_no_rules.com:8080 -> SiteEntry(None, expiry)
  *
  * "Custom User Agent" ->
  * map of cache entries ...
  *
  * "Custom User Agent" ->
  * map of cache entries ...
  *
  * </pre>
  *
  * @see    http://www.robotstxt.org
  */
class DefaultRobotRulesCache(val httpClient: HttpClient)(implicit val execContext: ExecutionContext) extends RobotRulesCache {

  private[robot] var caches: Map[String, Cache] = Map.empty

  override def getDelayFor(userAgent: String, reader: UriReader): Future[Option[Int]] =
    getSiteEntry(userAgent, reader.schemeToPort) map (_.rules.crawlDelay)

  override def allow(userAgent: String, reader: UriReader): Future[Boolean] =
    getSiteEntry(userAgent, reader.schemeToPort) map (_.rules.allow(reader.path))

  private def getSiteEntry(userAgent: String, key: String): Future[SiteEntry] = {
    val uaCache = caches.get(userAgent) match {
      case Some(cache) => cache
      case None =>
        val c = new Cache(Map.empty)
        caches += (userAgent -> c)
        c
    }

    val now = new DateTime

    // Evict expired entry
    uaCache.cache.get(key) match {
      case Some(SiteEntry(rr, expired)) =>
        if (now.isAfter(expired)) uaCache.cache -= key
      case None =>
    }

    uaCache.cache.get(key) match {
      case Some(se: SiteEntry) => Future.successful(se)
      case None =>
        for {
          robRules <- fetchParseRules(userAgent, key) // Not cached so go fetch
        } yield {
          val expires = new DateTime().plus(entryExpires.toInt)
          val se = SiteEntry(robRules, expires)
          uaCache.cache += (key -> se)
          se
        }
    }
  }

  def entryExpires: Long = 1.days.toMillis

  private def fetchParseRules(userAgent: String, baseUri: String): Future[RobotRules] = {
    val request = Get(userAgent, CrawlUri(s"$baseUri/robots.txt"))
    for {
      response <- httpClient.request(request)
    } yield rulesParser.parse(userAgent, response.contentString)
  }

  def rulesParser = new RobotRulesParser

  // Batches up calls to the cache but may not be particularly efficient
  // because for each URI reader a cache access is made to getSiteEntry.
  def allow(userAgent: String, uris: Seq[CrawlUri]): Future[Seq[(CrawlUri, Boolean)]] =
    Future.sequence(
      uris.map({ uri =>
        val r = uri.reader
        getSiteEntry(userAgent, r.schemeToPort) map (
          siteEntry => (uri, siteEntry.rules.allow(r.path)))
      }))

  private[robot] case class Cache(var cache: Map[String, SiteEntry])

  private[robot] case class SiteEntry(rules: RobotRules, expires: DateTime)
}