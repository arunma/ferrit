package org.ferrit.core.parser

import org.allenai.common.testkit.UnitSpec

import org.ferrit.core.http.{DefaultResponse, Get, Request, Response, Stats}
import org.ferrit.core.uri.CrawlUri
import org.ferrit.core.util.HttpUtil._
import org.mockito.Mockito._

class TestHtmlParserJsoup extends UnitSpec {

  val css_url = "css_url"

  behavior of "HtmlParserJsoup"


  it should "only parse HTML documents" in {

    def responseOf(contentType: String):Response = {
      val r = mock(classOf[Response])
      when (r.contentType) thenReturn Some(contentType)
      r
    }

    val parser = HtmlParserJsoup
    parser.canParse(responseOf("text/css")) should equal (false)
    parser.canParse(responseOf("html")) should equal (false)
    parser.canParse(responseOf("text")) should equal (false)
    parser.canParse(responseOf("")) should equal (false)
    parser.canParse(responseOf("text/html")) should equal (true)
    parser.canParse(responseOf("text/html; charset=UTF-8")) should equal (true)
    parser.canParse(responseOf("text/html;charset=UTF-8")) should equal (true)

    intercept[ParseException] { parser.parse(responseOf("text/css")) }
    intercept[ParseException] { parser.parse(responseOf("")) }

  }

  it should "ignore empty attribute values" in {
    val html = """
        |<!doctype html>
        |<html>
        |  <head>
        |    <title>Test Page</title>
        |    <script src=""></script>
        |    <script src=" "></script>
        |    <style>@import url( );</style>
        |    <style>@import url('');</style>
        |    <style>@import url('  ');</style>
        |  </head>
        |<body>
        |  <h1>Title</h1>
        |  <a href="">empty</a>
        |  <a href="  ">empty</a>
        |  <img src="" />
        |  <img src="  " />
        |</body>
        |</html>
        """.stripMargin

    val parser = HtmlParserJsoup
    val request = makeRequest("http://site.com/page1.html")
    val response = makeResponse(html, request)
    val result: ParserResult = parser.parse(response)
    result.links.size should equal (0)

  }

  it should "parse links from various elements" in {

    val html = """
        |<!doctype html>
        |<html>
        |  <head>
        |    <title>Test Page</title>
        |    <script src="script.js"></script>
        |    <style>@import url('screen.css');</style>
        |  </head>
        |<body>
        |  <h1>Title</h1>
        |  <p>Follow <a href="page2.html">this link</a> for more.</p>
        |  <p>An image <img src="cat_photo.png" rel="nofollow" /> of you.</p>
        |</body>
        |</html>
        """.stripMargin

    val parser = HtmlParserJsoup
    val request = makeRequest("http://site.com/page1.html")
    val response = makeResponse(html, request)
    val result: ParserResult = parser.parse(response)

    result.links should contain(Link("img", "cat_photo.png", "", noFollow = true, Some(CrawlUri("http://site.com/cat_photo.png")), None))
    result.links should contain(Link("script", "script.js", "", noFollow = false, Some(CrawlUri("http://site.com/script.js")), None))
    result.links should contain(Link("a", "page2.html", "this link", noFollow = false, Some(CrawlUri("http://site.com/page2.html")), None))
    result.links.size should equal(3)

  }

  it should "exclude <base> elements from link extraction" in {

    val html = """
        |<html><head>
        |<base href="should be ignored"></base>
        |</head><body/></html>""".stripMargin

    val parser = HtmlParserJsoup
    val request = makeRequest("http://site.com/page1.html")
    val response = makeResponse(html, request)
    val result: ParserResult = parser.parse(response)

    result.links should equal (Set.empty)

  }

  it should "parse links with respect to base tag" in {

    val html = """
        |<!doctype html>
        |<html>
        |  <head>
        |    <base href="http://site3.com"></base>
        |    <title>Test Page</title>
        |  </head>
        |<body>
        |  <h1>Title</h1>
        |  <p>Follow <a href="page1.html">this link</a> for more.</p>
        |  <p>Follow <a href="http://site2.com/page2.html">this link</a> for more.</p>
        |</body>
        |</html>
        """.stripMargin

    val parser = HtmlParserJsoup
    val request = makeRequest("http://site.com/page1.html")
    val response = makeResponse(html, request)
    val result: ParserResult = parser.parse(response)

    result.links should contain(Link("a", "http://site2.com/page2.html", "this link", noFollow = false, Some(CrawlUri("http://site2.com/page2.html")), None))
    result.links should contain(Link("a", "page1.html", "this link", noFollow = false, Some(CrawlUri("http://site3.com/page1.html")), None))
    result.links.size should equal (2)

  }

  it should "identify the meta noindex directive" in {

    val parser = HtmlParserJsoup

    def expecting(expected: Boolean, html: String) = {
      val request = makeRequest("http://site.com")
      val response = makeResponse(html, request)
      parser.parse(response).indexingDisallowed should equal (expected)
    }

    val html =
      """<html><head>
      |<meta name="robots" content="%s" />
      |</head><body/></html>""".stripMargin

    expecting(expected = true, html.format("noindex"))
    expecting(expected = true, html.format("NOINDEX"))
    expecting(expected = true, html.format("noindex, nofollow"))
    expecting(expected = true, html.format("nofollow, noindex"))
    expecting(expected = false, html.format("nofollow"))
    expecting(expected = false, html.format("noindexing"))

  }

  it should "identify the meta nofollow directive" in {

    val parser = HtmlParserJsoup

    def expecting(expected: Boolean, html: String) = {
      val request = makeRequest("http://site.com")
      val response = makeResponse(html, request)
      parser.parse(response).followingDisallowed should equal (expected)
    }

    val html =
      """<html><head>
      |<meta name="robots" content="%s" />
      |</head><body/></html>""".stripMargin

    expecting(expected = true, html.format("nofollow"))
    expecting(expected = true, html.format("NOFOLLOW"))
    expecting(expected = true, html.format("noindex, nofollow"))
    expecting(expected = true, html.format("nofollow, noindex"))
    expecting(expected = false, html.format("noindex"))
    expecting(expected = false, html.format("nofollowing"))

  }

  it should "handle parse failures" in {

    val html =
      """<html><head>
      |<link href="bad_relative_uri1.css?id" rel="stylesheet" />
      |<link href="bad_relative_uri2.css?id===" rel="stylesheet" />
      |<link href="bad_relative_uri3.css?id" rel="stylesheet" />
      |</head><body/></html>""".stripMargin

    val request = makeRequest("http://site.com/page1.html")
    val response = makeResponse(html, request)
    val result: ParserResult = HtmlParserJsoup.parse(response)

    result.links.size should equal (3)
    result.links.count(_.crawlUri.nonEmpty) should equal(2)
    result.links.count(_.failMessage.nonEmpty) should equal(1)

  }

  /* = = = = = = = = = =  Utility  = = = = = = = = = = */

  def makeRequest(uri: String) = Get("*", CrawlUri(uri))

  /**
   * Better just to use a default Response as a mock object doesn't buy very much
   * and in fact leads to more verbose and buggy behaviour.
   */
  def makeResponse(content: String, request: Request) = DefaultResponse(
    200,
    Map(ContentTypeHeader -> Seq(TextHtmlUtf8)),
    content.getBytes,
    Stats.empty,
    request
  )

}
