package org.ferrit.core.robot

import org.ferrit.core.uri.UriReader

import scala.concurrent.Future

/** <p>The robots cache answers two questions during a crawl:</p>
  *
  * <ol>
  *   <li>Is the given URI allowed to be visited for the given user agent?</li>
  *   <li>What is the crawl delay for the given website and user agent?</li>
  * </ol>
  *
  * @see http://www.robotstxt.org
  */
trait RobotRulesCache {

  /** @return crawl delay -
    *    if present, is the minimum delay in seconds between fetches
    *    requested by the server for the given user agent.
    *    The robots txt file is fetched and parsed if not cached.
    */
  def getDelayFor(userAgent: String, reader: UriReader): Future[Option[Int]]

  /** @return true -
    *    if URI of the given reader and user agent can be crawled.
    *    The robots txt file is fetched and parsed if not cached.
    */
  def allow(userAgent: String, reader: UriReader): Future[Boolean]

}
