package org.ferrit.core.model

import org.ferrit.core.crawler.CrawlConfig
import org.ferrit.core.util.UniqueId

case class Crawler(crawlerId: String, config: CrawlConfig)

object Crawler {
  def create(config: CrawlConfig): Crawler = {
    val id = UniqueId.next
    Crawler(id, config.copy(id = id))
  }
}