package org.ferrit.core.robot

import org.allenai.common.testkit.UnitSpec

import org.ferrit.core.http.{DefaultResponse, HttpClient, Request, Response, Stats}
import org.ferrit.core.test.FakeHttpClient.TextResponse
import org.ferrit.core.test.ParrotHttpClient
import org.ferrit.core.uri.CrawlUri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class TestDefaultRobotRulesCache extends UnitSpec {

  behavior of "RobotRulesCache"

  val execContext = global
  val userAgent = "Mock Agent"
  val mockStats = Stats.empty


  def awaitAllow(cache: RobotRulesCache)(curi: CrawlUri):Boolean =
    Await.result(cache.allow(userAgent, curi.reader), 5.seconds)

  def awaitGetDelayFor(cache: RobotRulesCache)(curi: CrawlUri):Option[Int] =
    Await.result(cache.getDelayFor(userAgent, curi.reader), 5.seconds)


  trait MockClient extends HttpClient {
    override def shutdown():Unit = { }
  }

  it should "determine crawl delay" in {

    // Remember: crawl delay is expressed in seconds not millis

    val site1 = "http://site1.com"
    val site2 = "http://site2.com"
    val site3 = "http://site3.com" // not in map

    val contentMap = Map(
      s"$site1/robots.txt" -> TextResponse("""
                  |User-agent: *
                  |crawl-delay: 1""".stripMargin),
      s"$site2/robots.txt" -> TextResponse("""
                  |User-agent: *
                  |crawl-delay: 2 """.stripMargin)
    )

    val client = new ParrotHttpClient(contentMap)
    val cache = new DefaultRobotRulesCache(client)
    def awaitDelay = awaitGetDelayFor(cache) _

    awaitDelay(CrawlUri(s"$site1/page1")) should equal (Some(1000))
    awaitDelay(CrawlUri(s"$site2/page1")) should equal (Some(2000))
    awaitDelay(CrawlUri(s"$site3/page1")) should equal (None)

  }

  it should "exhibit correct allows behaviour" in {

    // Tests robots cache with simulated robots.txt fetch behaviour.
    // We get pre-parsed RobotRules from Map instead of the Internet.
    // If site is not in the map we pretend it has no robots.txt file.

    val site1 = "http://site1.com"
    val site2 = "http://site2.com"
    val site3 = "http://site3.com"
    val site4 = "http://site4.com" // not in map

    val contentMap = Map(
      site1 -> """|User-agent: *
                  |disallow: /page1""".stripMargin,
      site2 -> """|User-agent: *
                  |disallow: /page2""".stripMargin,
      site3 -> "" // edge case: found but empty file
    )

    var founds = 0 // total robots.txt found
    var notFounds = 0 // total robots.txt not found

    val client = new MockClient {
      def request(r: Request): Future[Response] =
        Future {
          val site = r.crawlUri.reader.schemeToPort
          val content = contentMap.get(site) match {
            case Some(c) =>
              founds += 1
              c
            case None =>
              notFounds += 1
              ""
          }
          DefaultResponse(-1, Map.empty, content.getBytes, mockStats, r)
        }
    }

    val cache = new DefaultRobotRulesCache(client)

    def awaitDelay = awaitGetDelayFor(cache) _
    def awaitAllows = awaitAllow(cache) _

    awaitAllows(CrawlUri(s"$site1/page1")) should equal(false)
    awaitAllows(CrawlUri(s"$site1/page2")) should equal(true)
    founds should equal (1)

    awaitAllows(CrawlUri(s"$site2/page1")) should equal(true)
    awaitAllows(CrawlUri(s"$site2/page2")) should equal(false)
    founds should equal (2)

    awaitAllows(CrawlUri(s"$site3/page1")) should equal(true)
    awaitAllows(CrawlUri(s"$site3/page2")) should equal(true)
    founds should equal (3)

    awaitAllows(CrawlUri(s"$site4/page1")) should equal(true)
    awaitAllows(CrawlUri(s"$site4/page2")) should equal(true)
    founds should equal (3)
    notFounds should equal (1)

    // Important: try again on site1 after site2 not founds populated
    awaitAllows(CrawlUri(s"$site1/page1")) should equal(false)
    awaitAllows(CrawlUri(s"$site1/page2")) should equal(true)
    founds should equal (3)
    notFounds should equal (1)

    // Same host different port
    awaitAllows(CrawlUri(s"$site1:8080/page1")) should equal(true)
    awaitAllows(CrawlUri(s"$site1:8080/page2")) should equal(true)
    awaitAllows(CrawlUri(s"$site2:8080/page2")) should equal(true)
    awaitAllows(CrawlUri(s"$site4:8080/page1")) should equal(true)
    founds should equal (3)
    notFounds should equal (4)

  }

  it should "only refetch when cache entries expired" in {

    val expiresMs = 25
    var refetches = 0 // counts number of cache invalidations and refetches

    val client = new MockClient {
      override def request(r: Request): Future[Response] = {
        refetches += 1 // should only refetch when cache empty or invalidated
        Future {
          DefaultResponse(-1, Map.empty, "".getBytes, mockStats, r)
        }
      }
    }

    val cache = new DefaultRobotRulesCache(client) {
      // override default expiry to speed up tests
      override def entryExpires = expiresMs
    }

    // Issue a variety of calls that would trigger the cache checking.
    // A cache refresh should only happen once per batch of calls
    // because they all occur before cache expiry.

    val curi = CrawlUri("http://site1.com/page1")
    def awaitDelay = awaitGetDelayFor(cache) _
    def awaitAllows = awaitAllow(cache) _

    awaitAllows(curi)
    awaitAllows(curi)
    awaitDelay(curi)
    awaitDelay(curi)
    refetches should equal (1)

    Thread.sleep(expiresMs + 1) // wait until expired
    awaitAllows(curi)
    refetches should equal (2) // Should increment because cache refresh required
    awaitAllows(curi)
    awaitDelay(curi)
    awaitDelay(curi)
    refetches should equal (2) // No change expected

  }


  /* = = = = = = = Batch URI Testing = = = = = = = */

    it should "exhibit correct allows behaviour for batches of URIs" in {

    // Tests robots cache with simulated robots.txt fetch behaviour.
    // We get pre-parsed RobotRules from Map instead of the Internet.
    // If site is not in the map we pretend it has no robots.txt file.

    val site1 = "http://site1.com"
    val site2 = "http://site2.com"
    val site3 = "http://site3.com"
    val site4 = "http://site4.com" // simulate robots.txt not found

    val responses = Map(
      s"$site1/robots.txt" -> TextResponse("""
                  |User-agent: *
                  |disallow: /page1
                  |disallow: /page4
                  """.stripMargin),
      s"$site2/robots.txt" -> TextResponse("""
                  |User-agent: *
                  |disallow: /page2
                  |disallow: /page4
                  """.stripMargin),
      s"$site3/robots.txt" -> TextResponse("") // edge case: file found but empty
    )

    val client = new ParrotHttpClient(responses)

    def batchTest(data: Seq[(String,Boolean)]) = {

      val cache = new DefaultRobotRulesCache(client)
      val expectedResult = data.map(pair => (CrawlUri(pair._1), pair._2))
      val uris = expectedResult.map(_._1)

      val result: Seq[(CrawlUri,Boolean)] = Await.result(
        cache.allow(userAgent, uris),
        10.millis
      )
      result should equal (expectedResult)
    }

    batchTest(Seq(
      (site1, true),
      (site2, true),
      (site3, true),
      (site4, true)
    ))
    batchTest(Seq(
      (site1 + "/page1", false),
      (site2 + "/page1", true),
      (site3 + "/page1", true),
      (site4 + "/page1", true)
    ))
    batchTest(Seq(
      (site1 + "/page2", true),
      (site2 + "/page2", false),
      (site3 + "/page2", true),
      (site4 + "/page2", true)
    ))
    batchTest(Seq(
      (site1 + "/page3", true),
      (site2 + "/page3", true),
      (site3 + "/page3", true),
      (site4 + "/page3", true)
    ))

  }

  // it should "fetch and parse real robots.txt file" in {

  //   val ua = "WebResearch Agent"
  //   val site = "http://www.houghton-trail-event.org.uk"
  //   val client = new NingAsyncHttpClient
  //   val cache = new DefaultRobotRulesCache(client)

  //   val allowContact = awaitAllow(cache)(CrawlUri(s"$site/contact"))
  //   val allowInfo = awaitAllow(cache)(CrawlUri(s"$site/information"))

  //   allowContact should equal (false)
  //   allowInfo should equal (true)

  //   client.shutdown()
  // }

}
