package org.ferrit.dao

import org.ferrit.core.model._
import org.joda.time.DateTime

trait CrawlerDAO {
  def insert(crawler: Crawler): Unit

  def delete(crawlerId: String): Unit

  def find(crawlerId: String): Option[Crawler]

  def findAll(): Seq[Crawler]
}

trait DocumentDAO {
  def insert(doc: Document): Unit

  def find(jobId: String, uri: String): Option[Document]
}

trait DocumentMetaDataDAO {
  def insert(docMeta: DocumentMetaData): Unit

  def find(jobId: String, uri: String): Option[DocumentMetaData]

  def find(jobId: String): Seq[DocumentMetaData]
}

trait CrawlJobDAO {
  def insertByCrawler(jobs: Seq[CrawlJob]): Unit

  def insertByDate(jobs: Seq[CrawlJob]): Unit

  def find(crawlerId: String, jobId: String): Option[CrawlJob]

  def find(crawlerId: String): Seq[CrawlJob]

  def find(partitionDate: DateTime): Seq[CrawlJob]
}

trait FetchLogEntryDAO {
  def insert(fle: FetchLogEntry): Unit

  def insert(entries: Seq[FetchLogEntry]): Unit

  def find(jobId: String): Seq[FetchLogEntry]
}
