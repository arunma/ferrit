package org.ferrit.core.crawler

/** A CrawlRejectException is a non-recoverable exception that occurs when a crawler job
  * cannot be started. This may be because something is wrong with the CrawlConfig being used
  * for the job or that the CrawlerManager refuses to start the job for various reasons.
  */
case class CrawlRejectException(msg: String) extends RuntimeException(msg)
