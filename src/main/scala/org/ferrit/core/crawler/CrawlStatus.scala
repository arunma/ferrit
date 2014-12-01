package org.ferrit.core.crawler

import org.joda.time.DateTime

case class CrawlStatus(
  crawlStart: DateTime = new DateTime,
  crawlStop: DateTime,
  alive: Boolean = true,
  shouldStop: Boolean = false
) {
  def stop = copy(shouldStop = true)
  def dead = copy(alive = false)
  def now = new DateTime
}
