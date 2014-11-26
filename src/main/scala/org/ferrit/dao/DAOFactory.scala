package org.ferrit.dao

trait DAOFactory {
  val crawlerDao: CrawlerDAO

  val crawlJobDao: CrawlJobDAO

  val fetchLogEntryDao: FetchLogEntryDAO

  val documentMetaDataDao: DocumentMetaDataDAO

  val documentDao: DocumentDAO
}

