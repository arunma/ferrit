package org.ferrit.core.test

import org.ferrit.core.http.{Request, Response}
import org.ferrit.core.test.FakeHttpClient.NotFound
import org.ferrit.core.util.Headers

import scala.concurrent.ExecutionContext


/**
 * Generates a website of N pages to support a crawler reachability test and
 * work out the crawler through a very large number of pages quickly.
 *
 * Each page contains exactly one hyperlink
 * to the next page in the sequence which the crawler is expected to follow.
 * The last page can only be reached by crawling through all the previous links. 
 * Such a site containing N pages will also be considered a site of depth N.
 *
 * Page 0 is the index page and the last is Page N-1
 *
 * <pre>
 *
 * Example assuming the domain http://site.net:
 *
 * A request to http://site.net returns:
 *
 *   <html>
 *     <a href="http://site.net/page1.html">
 *   </html>
 *
 *
 * A request to http://site.net/page1.html returns:
 *
 *   <html>
 *     <a href="http://site.net/page2.html">
 *   </html>
 * 
 * ...
 *
 * A request to http://site.net/page(N-2).html returns:
 * (where N-2 is the penultimate)
 * In a site of 100 pages the last page is page99.html because the index page
 * is treated as the first page (of zero index).
 *
 *   <html>
 *     <a href="http://site.net/page(N-1).html">
 *   </html>
 *
 * Page N
 *   <html>
 *     <!-- no link is included -->
 *   </html>
 *
 * <pre>
 *
 * @param domainName - the scheme, domain and port of the fake website 
 *                     (e.g. http://www.site.net:80)
 * @param totalPages - the number of fake pages that exist on the site
 *
 */
class LinkedListHttpClient(domainName: String, totalPages: Int)(implicit ec: ExecutionContext) extends FakeHttpClient {

  override implicit val _ec: ExecutionContext = ec
  val headers = Map(Headers.ContentTypeTextHtmlUtf8)
  val linkHtml = """<a href="%s/page%s.html">link text</a>"""

  // The HTML is small because the larger the template the more work 
  // the link extracting HTML parser will need to do per page.
  val UriPath = """page(\d+)\.html""".r
  val html = """
        |<!DOCTYPE html>
        |<html lang="en-US">
        |<head>
        |  <title>Test Page Number %s</title>
        |</head>
        |<body>
        |  <!-- BEGIN -->
        |       %s
        |  <!-- END -->
        |</body>
        |</html>
        """.stripMargin

  override def handleRequest(request: Request):Response = {

    // Inspect URI and search for a path like "pageN.html"
    // where N is a number within the totalPages total.

    val uri = request.crawlUri
    val reader = uri.reader

    val pr:PartResponse = {
      
      if (uri.toString == domainName) {
        // Is request for index page, return first page
        PartResponse(200, headers, makePage(0, 1))

      } else if (domainName != reader.schemeToPort) {
        // URI was for other website or different port
        NotFound 

      } else {
        val pageNum = for {
          UriPath(n) <- UriPath.findFirstMatchIn(reader.path)
        } yield n

        pageNum match {
          case Some(numStr) =>
            val num = Integer.parseInt(numStr)
            if (num < 1 || num > totalPages) NotFound
            else PartResponse(200, headers, makePage(num, num + 1))
          case _ => NotFound
        }
      }
    }

    pr.toResponse(request)
  }

  private [test] def makePage(pageNum:Int, linkNum: Int): String = {
    val isLast = linkNum == totalPages
    if (isLast) html.format(pageNum, "")
    else html.format(
      pageNum,
      linkHtml.format(domainName, linkNum)
    )
  }

}

