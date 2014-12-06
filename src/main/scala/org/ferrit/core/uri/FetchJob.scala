package org.ferrit.core.uri

/** Whenever the target of a URI needs fetching a fetch job is created
  * and added to the Frontier queue.
  * A fetch job essentially stores a URI but requires a crawl depth to
  * ensure crawl jobs do not overrun their depth limit.
  */
case class FetchJob(uri: CrawlUri, depth: Int)
