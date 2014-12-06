package org.ferrit.core.crawler

import org.allenai.common.testkit.UnitSpec

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.ferrit.core.filter.FirstMatchUriFilter
import org.ferrit.core.filter.FirstMatchUriFilter.Accept
import org.ferrit.core.uri.CrawlUri

import scala.util.{Failure, Success}

class TestCrawlConfig extends UnitSpec {

  val uri = "http://website.net"

  val config = CrawlConfig(
    id = "12345678",
    userAgent = Some("Test Agent"),
    crawlerName = "Test Crawler",
    seeds = Seq(CrawlUri(uri)),
    uriFilter = new FirstMatchUriFilter(Seq(Accept(uri.r))),
    tests = Some(Seq(s"accept: $uri")),
    crawlDelayMillis = 0,
    crawlTimeoutMillis = 10000,
    maxDepth = Int.MaxValue,
    maxFetches = 10000,
    maxQueueSize = 10000,
    maxRequestFails = 0.5
  )

  def failureWithMessage(msg: String) = Failure(CrawlRejectException(msg))

  it should "succesfully validate a crawl config" in {
    config.validated should equal (Success(true))
  }

  it should "fail validation with missing user agent" in {
    val uaFailure = Failure(CrawlRejectException(CrawlConfig.UserAgentMissing))
    config.copy(userAgent = None).validated should equal (uaFailure)
    config.copy(userAgent = Some(" ")).validated should equal (uaFailure)
  }

  it should "fail validation when seeds are missing" in {
    val seedsFailure = Failure(CrawlRejectException(CrawlConfig.SeedsAreMissing))
    config.copy(seeds = null).validated should equal (seedsFailure)
  }

  it should "fail validation when seeds are rejected" in {
    val otherSite = "http://othersite.net"
    config.copy(seeds = Seq(CrawlUri(otherSite))).validated match {
      case Success(b) => fail("Seeds should have been rejected")
      case Failure(t) =>
    }
  }

}
