package org.ferrit.core.uri

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import spray.http.Uri

/**
 * There are multiple issues with this code that still need resolving.
 *
 * The URI resolution tests were adapted from Java HtmlUnit project.
 * The normalization tests adapted from crawler4j URLCanonicalizerTest.
 *
 * @see http://www.faqs.org/rfcs/rfc1808.html (RFC1808 Section 5.1 examples)
 * @see http://en.wikipedia.org/wiki/URL_normalization
 * @see http://en.wikipedia.org/wiki/Percent-encoding#Types_of_URI_characters
 * @see https://groups.google.com/forum/#!topic/spray-user/8DFoFH4jEBo
 * @see https://github.com/spray/spray/issues/304
 * @see HtmlUnit { @see http://code.google.com/p/crawler4j/source/browse/src/test/java/edu/uci/ics/crawler4j/tests/URLCanonicalizerTest.java
 *
 */
class TestCrawlUri extends FlatSpec with ShouldMatchers {
  
  behavior of "CrawlUri"

  
  def crawlableUri(uri: String) = CrawlUri(uri).crawlableUri

  it should "URIs normalised to same URI string should have same equals and hashCode" in {
    
    CrawlUri("http://website.com:80").hashCode should equal (
      CrawlUri("http://website.com").hashCode
    )
    CrawlUri("http://website.com:80") should equal (
      CrawlUri("http://website.com")
    )
    
  }

  it should "produce normalized toString" in {
    
    CrawlUri("http://www.site.com:8080?id=100&name=foo").toString should equal (
      "http://www.site.com:8080?id=100&name=foo"
    )
    CrawlUri("http://website.com:80").toString should equal (
      "http://website.com"
    )

  }

  it should "convert default ports to 0 (spray compatible)" in {
    Uri("http://site.com:80").authority.port should equal (0)
    Uri("ssh://site.com:22").authority.port should equal (0)
    CrawlUri("http://site.com").reader.schemeToPort should equal ("http://site.com")
    CrawlUri("http://site.com:80").reader.schemeToPort should equal ("http://site.com")
  }

  it should "have identical hashcode for identical Uri" in {
    
    val s1 = new String("http://www.site.net:8080?id=100&name=foo")
    
    // Try fool VM into creating a whole different String, probably won't work!
    val sb = new StringBuilder
    sb.append("http")
      .append("://www.site.net:8080")
      .append("?id=100&name=foo")
    val s2 = sb.toString

    val cu1 = CrawlUri(s1)
    val cu2 = CrawlUri(s2)
    
    s1.hashCode should equal (-772506185)
    s1.hashCode should equal (s2.hashCode)
    cu1.hashCode should equal (-772506185)
    cu1.hashCode should equal (cu2.hashCode)
    
  }

  it should "be identical when having identical raw URI strings" in {
    
    // A default CrawlUri is a SprayCrawlUri which is a case class
    // so we'd expect a sensible equals when URI strings are the same

    val urls = List(
      "/",
      "/unencoded path",
      "http://www.website.com/page1.html",
      "http://www.website.com/page1.html?id=100&name=theName",
      "http://www.website.com/page1.html?id=100&name=theName#frag",
      "http://localhost/jdk-7u4-apidocs/api/java/awt/event/InputEvent.html"
    )
    urls.foreach(url => {
      val s1 = new String(url)
      val s2 = new String(url)
      (s1 eq s2) should equal (false) // Java equals, proves different strings
      CrawlUri(s1) should equal (CrawlUri(s2))
    })
  }

  it should "parse URI path" in {

    // @see http://en.wikipedia.org/wiki/URI_scheme
    // parameters are part of the path

    // Spray prepends // to authority as per spec whereas Java URI does not.
    // See: tools.ietf.org/html/rfc3986#section-5.3
    //
    //   if defined(authority) then
    //      append "//" to result;
    //      append authority to result;
    //   endif;

    val curi = CrawlUri(
      "http://www.website.net:1234" + 
      "/path/to/file.htm;pname=pvalue?id=100&name=someName#frag"
    )
    val parameters = "pname=pvalue"
    val path = "/path/to/file.htm" + ";" + parameters

    curi.reader.path should equal (path)
  }

  /**
   * Test to ensure CrawlUri implementation handles underlying issues
   * in the real URI implementation.
   */
  it should "normalize away bad URIs" in {

    // Spray Uri won't accept white spaces in URI strings
    CrawlUri("unencoded path").crawlableUri should equal ("unencoded%20path")
    CrawlUri("unencoded path").reader.path should equal ("unencoded%20path")
    CrawlUri("/unencoded path").reader.path should equal ("/unencoded%20path")

    // Spray blows up with \n
    CrawlUri("\nhttp://site.com/image.jpg") should equal (
      CrawlUri("http://site.com/image.jpg")
    )

  }

  it should "resolve relative URI - normal examples" in {

    val base = CrawlUri("http://a/b/c/d;p?q#f")
    def resolvedUri(uri: String) = 
      CrawlUri(uri).absoluteCrawlableUri(base).crawlableUri

    resolvedUri("g:h") should equal ("g:h")
    resolvedUri("g") should equal ("http://a/b/c/g")
    resolvedUri("./g") should equal ("http://a/b/c/g")
    resolvedUri("g/") should equal ("http://a/b/c/g/")
    resolvedUri("/g") should equal ("http://a/g")
    resolvedUri("/g") should equal ("http://a/g")
    resolvedUri("//g") should equal ("http://g")
    resolvedUri("g?y") should equal ("http://a/b/c/g?y")
    resolvedUri("g?y/./x") should equal ("http://a/b/c/g?y/./x")
    resolvedUri("?y") should equal ("http://a/b/c/d;p?y")
    resolvedUri("g;x") should equal ("http://a/b/c/g;x")
    resolvedUri(".") should equal ("http://a/b/c/")
    resolvedUri("./") should equal ("http://a/b/c/")
    resolvedUri("..") should equal ("http://a/b/")
    resolvedUri("../") should equal ("http://a/b/")
    resolvedUri("../g") should equal ("http://a/b/g")
    resolvedUri("../..") should equal ("http://a/")
    resolvedUri("../../g") should equal ("http://a/g")

    // Altered tests: because hash fragment is removed before crawling
    resolvedUri("#s") should equal ("http://a/b/c/d;p?q") // original: "http://a/b/c/d;p?q#s"
    resolvedUri("#s") should equal ("http://a/b/c/d;p?q") // original: "http://a/b/c/d;p?q#s"
    resolvedUri("g#s") should equal ("http://a/b/c/g") // original: "http://a/b/c/g#s"
    resolvedUri("g#s/./x") should equal ("http://a/b/c/g") // original: "http://a/b/c/g#s/./x"
    resolvedUri("g?y#s") should equal ("http://a/b/c/g?y") // original: "http://a/b/c/g?y#s"
    resolvedUri("g;x?y#s") should equal ("http://a/b/c/g;x?y") // original: "http://a/b/c/g;x?y#s"

    // Failing with Spray
    //resolvedUri(";x") should equal ("http://a/b/c/d;x")

  }

  /**
   * The <base> element only affects relative URLs on a page.
   * Need to test that absolute URLs resolve the authority.
   */
  it should "prefer absolute URI over base URI" in {
    val base = CrawlUri("http://www.site.net/path/to/file.htm")
    val uri  = CrawlUri("http://www.site2.net/path/to/other/file.htm")
    uri.absoluteCrawlableUri(base).crawlableUri should equal (
      "http://www.site2.net/path/to/other/file.htm"
    )
  }

  it should "normalize URIs" in {

    // Spaces in path are converted to %20.
    // Compare: spaces in query strings converted to +)
    crawlableUri("http://www.website.com/A PDF File.pdf") should equal (
      "http://www.website.com/A%20PDF%20File.pdf"
    )
    
    crawlableUri("http://WEBSITE.com") should equal (
      "http://website.com"
    )

    crawlableUri("http://www.website.com:80/bar.html") should equal (
      "http://www.website.com/bar.html"
    )
    
    // Section "Normalizations that usually preserve semantics"
    // --------------------------------------------------------
    // See: "Adding trailing /"
    // http://en.wikipedia.org/wiki/URL_normalization
    //
    // crawler4j UrlNormalizer converts: http://website.com/ -> http://website.com
    // This is probably incorrect and Spray is doing the right thing here.

    crawlableUri("http://website.com/") should equal (
      "http://website.com/"
    )

    // See: "Decoding percent-encoded octets of unreserved characters."
    // ----------------------------------------------------------------
    // For consistency, percent-encoded octets in the ranges of ALPHA (%41–%5A 
    // and %61–%7A), DIGIT (%30–%39), hyphen (%2D), period (%2E), underscore (%5F), 
    // or tilde (%7E) should not be created by URI producers and, when found in 
    // a URI, should be decoded to their corresponding unreserved characters by 
    // URI normalizers.

    crawlableUri("http://www.site.com/display?category=foo/bar+baz") should equal (
      "http://www.site.com/display?category=foo/bar+baz"
    )

    // %2B equals 43 equals '+' but is not one of the percent-encoded chars
    // that is required to be decoded.
    // This means that a '+' is kept as a '+' but %2B not converted to '+' ?
    crawlableUri("http://www.website.com/display?category=foo%2Fbar%2Bbaz") should equal (
      "http://www.website.com/display?category=foo/bar%2Bbaz"
    )

    crawlableUri("http://www.website.com/?q=a+b") should equal (
      "http://www.website.com/?q=a+b"
    )

    // There is no need to encode unreserved characters such as ~
    // ----------------------------------------------------------
    // see: http://tools.ietf.org/html/rfc3986#section-2.4
    //   For example, the octet corresponding to the tilde ("~") character is often 
    //   encoded as "%7E" by older URI processing implementations; the "%7E" 
    //   can be replaced by "~" without changing its interpretation.

    // see: http://en.wikipedia.org/wiki/Percent-encoding
    //   For maximum interoperability, URI producers are discouraged from 
    //   percent-encoding unreserved characters.

    crawlableUri("http://www.website.com/%7Eusername?user=%7Eusername") should equal (
      "http://www.website.com/~username?user=~username"
    )

    crawlableUri("http://site.com/uploads/1/0/2/5/1025/6199.jpg?1325") should equal (
      "http://site.com/uploads/1/0/2/5/1025/6199.jpg?1325"
    )
  
    crawlableUri("http://site.com/page?content=<html>") should equal (
      "http://site.com/page?content=%3Chtml%3E"
    )

    crawlableUri("http://www.site.com/index.html?") should equal (
      //"http://www.site.com/index.html"
      "http://www.site.com/index.html?"
    )
    
    crawlableUri("http://www.website.com/index.html?&") should equal (
      //"http://www.website.com/index.html"
      "http://www.website.com/index.html?"
    )

    crawlableUri("http://www.website.com//A//B/index.html") should equal (
      //"http://www.website.com/A/B/index.html" // Spray does not remove double slash
      "http://www.website.com//A//B/index.html"
    )
    
    crawlableUri("http://www.website.com/index.html?&x=y") should equal (
      //"http://www.website.com/index.html?x=y" // Spray does not remove & after ?
      "http://www.website.com/index.html?&x=y"
    )
   
    crawlableUri("http://www.website.com/../../a.html") should equal (
      "http://www.website.com/a.html"
    )
    
    crawlableUri("http://www.website.com/../a/b/../c/./d.html") should equal (
      "http://www.website.com/a/c/d.html"
    )

    crawlableUri("http://www.website.com/index.html?id=1234&name=test#123") should equal (
      "http://www.website.com/index.html?id=1234&name=test"
    )
    
    // Spaces in the query string are converted to +
    crawlableUri("http://www.website.com/index.html?q=a b") should equal (
      "http://www.website.com/index.html?q=a+b"
    )
    
    crawlableUri("http://subdomain.website.com/?baz=1") should equal (
      "http://subdomain.website.com/?baz=1"
    )

  }

  it should "remove fragment from URI" in {
    crawlableUri("http://www.site.com?id=100#somefrag") should equal (
      "http://www.site.com?id=100"
    )
  }

  it should "sort query parameters" in {

    crawlableUri("http://www.site.com?id=100&name=foo&address=bar") should equal (
      "http://www.site.com?address=bar&id=100&name=foo"
    )

    crawlableUri("http://www.site.com/index.html?&c=d&e=f&a=b") should equal (
      //"http://www.site.com/index.html?a=b&c=d&e=f" // Spray doesn't remove & after ?
      "http://www.site.com/index.html?&a=b&c=d&e=f"
    )

    crawlableUri("http://www.website.com/image?width=200&height=100") should equal (
      "http://www.website.com/image?height=100&width=200"
    )

  }

  it should "remove session ID params" in {

    // Should strip known session ID params
    crawlableUri("http://www.site.com?jsessionid=100") should equal (
      "http://www.site.com"
    )
    crawlableUri("http://www.site.com?id=100&jsessionid=100&name=foo") should equal (
      "http://www.site.com?id=100&name=foo"
    )

  }

  // Tests are more or less duplicating those gone before

  it should "generate absolute crawlable URIs" in {
    
    val baseUri = CrawlUri("http://www.site.com")
    
    def absCrawlableUri(uri: String) = CrawlUri(baseUri, uri).crawlableUri
      
    // Should resolve to absolute URI
    absCrawlableUri("/page1") should equal (
      "http://www.site.com/page1"
    )

    // Should percent encode
    absCrawlableUri("http://site.com/page?content=<html>") should equal (
      "http://site.com/page?content=%3Chtml%3E"
    )

    // Absolute URL should not be overriden by base
    absCrawlableUri("http://www.site2.com/page1") should equal (
      "http://www.site2.com/page1"
    )

  }

  it should "pass on the correct depth" in {
    val uri1 = CrawlUri("http://site.net")
  }

}