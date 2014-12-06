package org.ferrit.core.crawler

import org.allenai.common.testkit.UnitSpec

import org.ferrit.core.crawler.CrawlConfigTester.{Result, Results}
import org.ferrit.core.filter.FirstMatchUriFilter
import org.ferrit.core.filter.FirstMatchUriFilter.Accept
import org.ferrit.core.uri.CrawlUri
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class TestCrawlConfigTester extends UnitSpec {

  import org.ferrit.core.crawler.CrawlConfigTester.Passed

  val baseConfig = CrawlConfig(
    id = "1234",
    userAgent = Some("Test Agent"),
    crawlerName = "Test Crawler",
    seeds = Nil,
    uriFilter = new FirstMatchUriFilter(Nil),
    tests = None,
    crawlDelayMillis = 0,
    crawlTimeoutMillis = 1,
    maxDepth = 1,
    maxFetches = 1,
    maxQueueSize = 1,
    maxRequestFails = 0
  )

  it should "find that a seed is accepted" in {

    val uri = "http://site.net"
    val config = baseConfig.copy(
      seeds = Seq(CrawlUri(uri)), 
      uriFilter = new FirstMatchUriFilter(Seq(Accept(uri.r))),
      tests = Some(Seq(s"should accept: $uri"))
    )

    CrawlConfigTester.testConfig(config) should equal (
      Results(
        allPassed = true,
        Seq(Result(CrawlUri(uri), passed = true, Passed)),
        Seq(Result(CrawlUri(uri), passed = true, Passed))
      )
    )

  }

  it should "find a seed rejected when it does not match the filter rules" in {

    val config: CrawlConfig = baseConfig.copy(
      seeds = Seq(CrawlUri("http://other.site.net")),
      uriFilter = new FirstMatchUriFilter(Seq(Accept("http://site.net".r))),
      tests = Some(Seq("should accept: http://site"))
    )

    val results = CrawlConfigTester.testConfig(config)
    results.allPassed should equal (false)    
    results.seedResults.size should equal (1)
    results.seedResults(0).passed should equal (false)

  }

  trait MultiUriTest {
    
    val uri1 = "http://site.net"
    val uri2 = "http://site.net/page1"
    val uri3 = "http://other.site.net/page1"
    val uri4 = "http://site.net/page2"
    val uris = Seq(uri1, uri2, uri3, uri4)

    def testWith(tests: Seq[String]) = {
      val config = baseConfig.copy(
        seeds = Seq(CrawlUri(uri2)),
        uriFilter = new FirstMatchUriFilter(Seq(Accept(uri1.r))),
        tests = Some(tests)
      )
      CrawlConfigTester.testConfig(config) 
    }

  }

  it should "test with accepted rules" in new MultiUriTest {
    val rules:Seq[String] = uris.map(u => s"should accept: $u")
    val results = testWith(rules)
    results.allPassed should equal (false)
    results.testResults.map(_.passed) should equal (Seq(true, true, false, true))
  }

  it should "test with reject rules" in new MultiUriTest {
    val rules:Seq[String] = uris.map(u => s"should reject: $u")
    val results = testWith(rules)
    results.allPassed should equal (false)
    results.testResults.map(_.passed) should equal (Seq(false, false, true, false))
  }

}
