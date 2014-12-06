package org.ferrit.core.parser

import org.ferrit.core.http.Response
import org.ferrit.core.uri.CrawlUri
import org.ferrit.core.util.{ MediaType, Stopwatch }
import org.jsoup.Jsoup
import org.jsoup.nodes.{ Document, Element }

object JsoupImplicits {

  import scala.collection.JavaConverters._
  import scala.language.implicitConversions

  implicit def elementsToSeq(elms: java.lang.Iterable[Element]): Seq[Element] =
    elms.asScala.toSeq
}

object HtmlParserJsoup extends ContentParser {

  import org.ferrit.core.parser.JsoupImplicits._

  val HtmlUriAttributes = Seq("href", "src", "cite", "data")

  override def parse(response: Response): ParserResult = {
    if (!canParse(response))
      throw new ParseException("Cannot parse response")
    val stopwatch = new Stopwatch
    val reqUri = response.request.crawlUri
    val doc = Jsoup.parse(response.contentString)
    val base = doc.select("base[href]").headOption match {
      case Some(e) => e.attr("href").trim match {
        case "" => reqUri // assume <base> not found
        case href => CrawlUri(reqUri, href)
      }
      case None => reqUri
    }

    val (links, noIndex, noFollow) = parseHtmlDoc(base, doc)
    DefaultParserResult(links, noIndex, noFollow, stopwatch.duration)
  }

  override def canParse(response: Response): Boolean =
    MediaType.is(response, MediaType.Html)

  def parseHtmlDoc(base: CrawlUri, doc: Document) = {
    // Check <meta> for noindex/nofollow directives
    val metaQuery = """meta[name=robots][content~=(?i)\b%s\b]"""
    val head = doc.head

    // Check elements with attributes like href/src for links. Ignores <base> elements.
    val links = for {
      attr <- HtmlUriAttributes
      e <- doc.select(s"[$attr]:not(base)")
      nfLink = if (head.select(metaQuery format "nofollow").nonEmpty) head.select(metaQuery format "nofollow").nonEmpty else "nofollow" == e.attr("rel").toLowerCase
      uriAttr = e.attr(attr).trim // e.g. src or href
      if !uriAttr.isEmpty
      (uri, failMsg) = makeUri(base, uriAttr)
    } yield Link(e.nodeName, uriAttr, e.text, nfLink, uri, failMsg)

    (links.toSet,
      head.select(metaQuery format "noindex").nonEmpty,
      head.select(metaQuery format "nofollow").nonEmpty)
  }

  def makeUri(base: CrawlUri, uriAttr: String): (Option[CrawlUri], Option[String]) =
    try {
      (Some(CrawlUri(base, uriAttr)), None)
    } catch {
      case t: Throwable => (None, Some(s"base[$base] relative[$uriAttr]"))
    }
}

