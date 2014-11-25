package org.ferrit.core.parser

import org.ferrit.core.http.Response
import org.ferrit.core.uri.CrawlUri
import org.ferrit.core.util.JsoupSugar.elementsToSeq
import org.ferrit.core.util.TagUtil.{CssImportUrl, CssTagEquiv, HtmlUriAttributes}
import org.ferrit.core.util.{MediaType, Stopwatch}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document


/**
 * A Jsoup backed HtmlParser to extract crawlable links.
 * *** Jsoup is totally freakin' cool by the way ***
 */
class HtmlParserJsoup extends ContentParser {

  override def parse(response: Response):ParserResult = {

    if (!canParse(response)) throw new ParseException(
      "Cannot parse response"
    )

    val stopwatch = new Stopwatch

    val reqUri = response.request.crawlUri
    val content: String = response.contentString
    val doc: Document = Jsoup.parse(content)

    // Work out if the base URL used for relative links in this document
    // will be either the document's own URL or a possible <base> tag.
    // (Jsoup can return a <base> with empty href attribute)

    val base:CrawlUri = doc.select("base[href]").headOption match {
      case Some(e) => e.attr("href").trim match {
          case "" => reqUri // assume <base> not found
          case href => CrawlUri(reqUri, href)
        }
      case None => reqUri
    }

    // Check <meta> for noindex/nofollow directives

    val metaQuery = """meta[name=robots][content~=(?i)\b%s\b]"""
    val head = doc.head
    val noIndex = head.select(metaQuery format "noindex").nonEmpty
    val noFollow = head.select(metaQuery format "nofollow").nonEmpty

    // Check elements with attributes like href/src for links.
    // Ignores <base> elements.

    var links: List[Link] = List.empty

    HtmlUriAttributes.foreach(attr => {
      doc.select(s"[$attr]:not(base)").toSeq
        .foreach(e => {
            val nfLink =
                if (noFollow) noFollow
                else "nofollow" == e.attr("rel").toLowerCase
            val uriAttr = e.attr(attr).trim // e.g. src or href
            if (!uriAttr.isEmpty) {
              val (uri, failMsg) = makeUri(base, uriAttr)
              val link = Link(
                  e.nodeName,
                  uriAttr,
                  e.text,
                  nfLink,
                  uri,
                  failMsg
              )
              links = link :: links
            }
        })
    })

    // Examine <style> for @import url('...')

    doc.select("style").toSeq foreach (style => {
      val styleLinks = (for {
        CssImportUrl(quote, uriAttr) <- CssImportUrl findAllMatchIn style.data
        if !uriAttr.trim.isEmpty
      } yield {
        val (absUri, failMsg) = makeUri(base, uriAttr)
        Link(CssTagEquiv, uriAttr, "", false, absUri, failMsg)
      }).toSeq
      links = styleLinks ++: links
    })

    DefaultParserResult(links.toSet, noIndex, noFollow, stopwatch.duration)

  }

  override def canParse(response: Response):Boolean =
    MediaType.is(response, MediaType.Html)
  
  private def makeUri(base:CrawlUri, uriAttr:String):(Option[CrawlUri], Option[String]) =
    try {
      (Some(CrawlUri(base, uriAttr)), None)
    } catch {
      case t: Throwable => (None, Some(s"base[$base] relative[$uriAttr]"))
    }
  
}
