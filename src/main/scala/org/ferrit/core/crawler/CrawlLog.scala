package org.ferrit.core.crawler

import akka.actor.{Actor, Props, Terminated}
import akka.event.Logging
import org.ferrit.core.crawler.CrawlWorker._
import org.ferrit.core.crawler.FetchMessages._
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.util.TextFormatter._
import org.joda.time.{DateTime, Duration}

/**
 * Register this with CrawlWorker to be notified of crawl events.
 * The fetch related messages will generate highly verbose output but
 * be helpful with debugging, for without this feedback it is hard to 
 * know what the crawler is doing.
 */
class CrawlLog extends Actor {
  private [crawler] val log = Logging(context.system, getClass)
  private [crawler] val w = 80 // width of display
  private [crawler] val debug = false

  override def receive = 
    receiveCrawlUpdate orElse 
    receiveFetchUpdate orElse 
    receiveOther

  def receiveCrawlUpdate: Receive = {

    // These messages occur once per crawl, at the beginning and end
    case StartOkay(msg, job) =>
        log.info("New crawl started: " + job.crawlerName)
    
    case StartFailed(t, config) => 
      List(
        line("-", w),
        " OOPS, CRAWL FAILED TO START",
        line("=", w),
        "",
        lcell("Crawler name:", 16," ") + "[" + config.crawlerName + "]",
        lcell("Time:", 16," ") + "[" + new DateTime + "]",
        "",
        line("=", w),
        ""
      ).foreach(log.error)
      log.error(t, s"Failed to start crawler, reason: ${t.getLocalizedMessage}")
      stop
    
    case EmptyFrontier => 
      logMsg("Fetch queue empty")
    
    case Stopped(outcome, job) => 
      crawlJobToLines(job).foreach(log.info)
      stop
  }

  def stop() = context.stop(self)

  def logMsg(msg: String) = log.info(msg)

  def crawlJobToLines(job: CrawlJob): Seq[String] = {
    val fc = job.fetchCounters
    val mc = job.mediaCounters
    val rc = job.responseCounters

    val duration = new Duration(job.createdDate, job.finishedDate.get)
    val totalFiles = fc.getOrElse(FetchSucceeds, 0)
    val avgFetchTime = new Duration(duration.getMillis / Math.max(totalFiles, 1))
    val allBytes = mc.map(pair => pair._2.totalBytes).sum

    val w = 80
    val hw = w / 2

    val outcome = job.outcome.getOrElse("Unknown")
    val message = job.message.getOrElse("No Message")

    val lines = List(

      List(
        "",
        line("-", w),
        " CRAWL FINISHED",
        line("=", w),
        "",
        lcell("Crawler name:", 16, " ") + "[" + job.crawlerName + "]",
        lcell("Stop time:", 16, " ") + "[" + new DateTime + "]",
        lcell("Crawl outcome:", 16, " ") + s"[$outcome, $message]",
        "",
        lcell("Duration: ", hw, ".") + rcell(" " + formatElapsedTime(duration.getMillis), hw, "."),
        lcell("Avg fetch time: ", hw, ".") + rcell(" " + formatElapsedTime(avgFetchTime.getMillis), hw, "."),
        lcell("Files fetched: ", hw, ".") + rcell(" " + totalFiles, hw, "."),
        lcell("Total content: ", hw, ".") + rcell(" " + formatBytes(allBytes), hw, "."),
        lcell("Redirects: ", hw, ".") + rcell(" " + fc.getOrElse(FetchRedirects, 0), hw, "."),
        lcell("Failed fetches: ", hw, ".") + rcell(" " + fc.getOrElse(FetchFails, 0), hw, ".")
      ),

      List("", "Total by content type:", ""),

      mc.keys.toSeq.sorted.map({ contentType =>
        mc.get(contentType) match {
          case Some(media) =>
            val fbytes = formatBytes(media.totalBytes)
            rcell("" + media.count, 8, " ") + rcell("" + fbytes, 14, " ") + "    " + contentType
          case None => ""
        }
      }),

      List("", "Total by response code:", ""),

      rc.map({ pair =>
        rcell("" + pair._2, 8, " ") + rcell("HTTP " + pair._1, 14, " ")
      }),

      List(
        "",
        line("=", 80),
        ""
      )
    )

    lines.flatten
  }


  /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */

  def receiveFetchUpdate: Receive = {

    // This set of messages occur once per resource crawled

    // A highly verbose message because it is emitted for every link
    // found in a resource, many of which will not be crawled.
    case FetchDecision(uri, decision) =>
      if (debug) logMsg(s"$decision [$uri]")

    case FetchQueued(f) =>
      if (debug) logMsg(s"Queued [${f.uri}] depth=${f.depth}")

    case FetchScheduled(f, delayMs) =>
      if (debug) logMsg(s"Fetching in [${delayMs}ms] [${f.uri}]")

    case FetchGo(f) =>
      if (debug) logMsg(s"Now fetching [${f.uri}]")

    case FetchResponse(uri, statusCode) =>
      if (debug) logMsg(s"HTTP [$statusCode] for [$uri]")

    case DepthLimit(f) =>
      if (debug) logMsg(s"Depth limit ${f.depth} for [${f.uri}]")

    case FetchResult(statusCode, fetchJob, cle, response, duration, parserResult) =>
      val uri = fetchJob.uri

      val msg = parserResult match {
        case Some(pr) => s"Found [${pr.links.size}] links in [$uri]"
        case None => s"Nothing extracted [$uri]"
      }
      if (debug) logMsg(msg)

      logMsg(s"Response $statusCode d=${fetchJob.depth} [$uri]")

    case FetchError(uri, t) =>
      logMsg(s"System error fetching [$uri], reason: ${t.getLocalizedMessage}")

  }

  def receiveOther: Receive = {
    case Terminated(_) => // Won't be received until death watch setup
    case msg => log.info(s"Unknown message: $msg")
  }
}

object CrawlLog {
  def props(): Props = Props(classOf[CrawlLog])
}