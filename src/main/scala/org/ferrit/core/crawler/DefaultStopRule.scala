package org.ferrit.core.crawler

import org.ferrit.core.util.Counters

class DefaultStopRule extends StopRule {
  import CrawlWorker._

  /** Allow a certain number of fails through before other considerations
    * otherwise a crawler with only 1 seed and a request fail
    * on the second page will abort too early.
    */
  val MinFailCount = 10

  override def ask(
    c: CrawlConfig,
    status: CrawlStatus,
    ct: Counters,
    fetchesPending: Int): CrawlOutcome = {
    if (status.shouldStop) StopRequested
    else if (fetchesPending == 0) CompletedOkay
    else if (fetchesPending >= c.maxQueueSize) TooManyQueued
    else if (ct.get(FetchSucceeds) >= c.maxFetches) TooManyFetches
    else if (status.now.isAfter(status.crawlStop)) CrawlTimeout
    else if (isFailedCrawl(
      c.seeds.size,
      c.maxRequestFails,
      ct.get(FetchAttempts),
      ct.get(FetchFails))) TooManyFetchesFailed
    else KeepCrawling
  }

  /** A simple computation to decide if a crawler is failing too much
    * and should stop. Factored out to be more testable, at the expense
    * of some boilerplate method signature.
    *
    * A fail percentage is calculated and if it exceeds the user-defined fail
    * percentage threshold then the crawl is considered failed.
    *
    * The percentage test is bypassed until all the seed URIs have been
    * actioned as fetch requests.
    *
    */
  private[crawler] def isFailedCrawl(
    totalSeeds: Int,
    maxFailPercent: Double,
    fetchAttempts: Int,
    fetchFails: Int) =
    !(fetchAttempts <= totalSeeds || fetchAttempts < MinFailCount) &&
      fetchFails.toDouble / fetchAttempts.toDouble >= maxFailPercent
}