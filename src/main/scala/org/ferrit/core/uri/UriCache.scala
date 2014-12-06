package org.ferrit.core.uri

/** Some kind of cache is needed to record which URIs were already seen by
  * a crawler and do not need fetching again.
  */
trait UriCache {
  def size: Int

  def put(uri: CrawlUri): Int

  def contains(uri: CrawlUri): Boolean
}