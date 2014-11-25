package org.ferrit.core.crawler

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.routing.Listen
import akka.testkit.{ImplicitSender, TestKit}
import org.ferrit.core.crawler.CrawlWorker._
import org.ferrit.core.crawler.FetchMessages._
import org.ferrit.core.filter.FirstMatchUriFilter
import org.ferrit.core.filter.FirstMatchUriFilter._
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.parser.MultiParser
import org.ferrit.core.robot.{RobotRulesCache, RobotRulesCacheActor}
import org.ferrit.core.test.FakeHttpClient._
import org.ferrit.core.test.{LinkedListHttpClient, MockRobotRulesCache, ParrotHttpClient, ProxyActor}
import org.ferrit.core.uri.{CrawlUri, InMemoryFrontier, InMemoryUriCache, UriReader}
import org.ferrit.core.util.{Media, UniqueId}
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random


class TestCrawlWorker extends FlatSpec with ShouldMatchers with BeforeAndAfterAll {
  
  implicit val system = ActorSystem("test")

  implicit val execContext = system.dispatcher
  val html = """
               |<!doctype html>
               |<html>
               |<head><title>Page Title</title>
               |<!-- insert css link -->
               |  %s
               |</head>
               |<body>
               |<!-- insert body content -->
               |  %s
               |</body>
               |</html>
             """.stripMargin
  val css =
    """#item { background: url('%s') }"""

  override def afterAll():Unit = system.shutdown()

  /**
   * To help debug test failures, a debug logger can be registered with
   * the Crawler that will then log the internal activitiy.
   * For example, just add this line before running crawler:
   *
   *   proxy !Listen(logger)
   *
   */
  def logger = system.actorOf(Props[CrawlLog])

  def mockRobotRulesCache = makeRobotRulesCache(new MockRobotRulesCache)

  def makeRobotRulesCache(cache: RobotRulesCache) =
    system.actorOf(Props(classOf[RobotRulesCacheActor], cache))

  /**
   * Each crawler Actor requires a different name else Akka complains.
   * Underlying reason is that test actors may still exist for some time after a
   * test has completed and names collide with the Actor in the next test.
   */
  def mkCrawlerName = "crawler-" + System.currentTimeMillis + "-" + Random.nextInt(100)

  /**
   * Generic config used by all tests, customisable by making a copy
   */
  def makeConfig(uri: String) = CrawlConfig(
    id = UniqueId.next,
    userAgent = Some("Test Agent"),
    crawlerName = "Test Crawler " + scala.util.Random.nextInt(10000),
    seeds = seedsFor(uri),
    uriFilter = uriFilterFor(uri),
    tests = Some(Seq(s"accept: $uri")),
    crawlDelayMillis = 0,
    crawlTimeoutMillis = 10000,
    maxDepth = Int.MaxValue,
    maxFetches = 10000,
    maxQueueSize = 10000,
    maxRequestFails = 0.5
  )

  def seedsFor(uri: String) = Seq(CrawlUri(uri))

  def uriFilterFor(uri: String) = new FirstMatchUriFilter(Seq(Accept(uri.r)))

  def makeJob(config: CrawlConfig) = CrawlJob.create(config, "localhost")

  class CrawlTest extends TestKit(system) with ImplicitSender {

    /**
     * The CrawlWorker is intended to be a child of CrawlerManager
     * with all communication to it mediated through the CrawlerManager.
     * Therefore we create a proxy parent for the CrawlWorker and talk
     * to the crawler through the proxy.
     *
     * Another reason for using this proxy:
     * It is possible to create a CrawlWorker using system.actorOf(...),
     * but messages sent by the CrawlWorker to it's
     * parent (which it thinks is the CrawlerManager) are not handled
     * by the guardian actor and end up logged as dead letters.
     */
    def makeCrawlerProxy(crawlerProps: Props): ActorRef = {
      val realParent = testActor // i.e. this TestKit
      system.actorOf(
        Props(classOf[ProxyActor], realParent, crawlerProps, mkCrawlerName)
      )
    }

  }

  behavior of "CrawlWorker" 

  it should "start and then stop by itself normally" in new CrawlTest {
    val config = makeConfig("http://site.net")
    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(Map.empty),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))

    proxy ! Listen(self)
    proxy ! Run
    fishForMessage(1.second) {
      case Stopped(CompletedOkay, job) => true
      case _ => false
    }
  }


  it should "respond to Stop request" in new CrawlTest {
    val site = "http://site.net"
    val config = makeConfig(site)
    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new LinkedListHttpClient(site, 2),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))

    proxy ! Listen(self)
    proxy ! Run
    fishForMessage(1.second) {
      case StartOkay(_, _) => true
      case _ => false
    }
    proxy ! StopCrawl
    fishForMessage(1.second) {
      case Stopped(StopRequested, job) => true
      case _ => false
    }
  }

  it should "crawl and find links in pages" in new CrawlTest {
    val site = "http://site.net"
    val responses = Map(
      s"$site" -> 
          HtmlResponse(html.format("",
            """<a href="page1.html">link</a>
               <a href="page2.html">link</a>
               <a href="page3.html">link</a>
               <a href="plain.txt">link</a>""")),
      s"$site/page1.html" -> 
          HtmlResponse(html.format("",
            """<a href="page1.html">link</a>""")),
      s"$site/page2.html" -> 
          HtmlResponse(html.format(
            """<link rel="stylsheet" type="text/css" href="styles.css" />""",
            """<a href="page3.html">link</a>""")),
      s"$site/plain.txt" -> 
          TextResponse("some plain text"),
      s"$site/styles.css" -> 
          CssResponse(css.format("spacecats.png")))

    val uriFilter = new FirstMatchUriFilter(Seq(
      Reject((site + """/.+\.txt""").r),
      Accept(site.r)
    ))

    val config = makeConfig(site).copy(uriFilter = uriFilter)

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(responses),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))


    proxy ! Listen(self)
    proxy ! Run

    fishForMessage(1.second) {
      case Stopped(CompletedOkay, job) => 
        List(job.fetchCounters, job.responseCounters, job.mediaCounters) should equal (List(
          Map(
            FetchAttempts -> 6, // 1 index, 3 html, 1 css, 1 png, but no .txt
            FetchSucceeds -> 4, // 3 html, 1 css
            FetchFails -> 2 // 1 page3.html, 1 png
          ),
          Map(
            "200" -> 4,
            "404" -> 2 // page3.html + png
          ),
          Map(
            "text/html; charset=UTF-8" -> Media(3, 744),
            "text/css; charset=UTF-8" -> Media(1, 42)
          )
        ))
        true
      case _ => false
    }
  }

  it should "ignore links containing unsupported schemes" in new CrawlTest {
    
    val site = "http://site.net"
    val responses = Map(
      s"$site" -> 
          HtmlResponse(html.format("",
            """<a href="http://site.net">link</a>
               <a href="mailto:someone">email</a>
               <a href="ftp://someplace">ftp location</a>"""))
      )

    val uriFilter = new FirstMatchUriFilter(Seq(Accept(site.r)))
    val config = makeConfig(site).copy(uriFilter = uriFilter)

    val crawler = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(responses),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))

    crawler ! Listen(self)
    crawler ! Run

    fishForMessage(1.second) {
      case Stopped(CompletedOkay, job) => 
        job.fetchCounters should equal (Map(FetchAttempts -> 1, FetchSucceeds -> 1))
        job.responseCounters should equal (Map("200" -> 1))
        true
      case _ => false
    }
    
  }

  it should "handle a larger number of pages" in new CrawlTest {

    val site = "http://site.net"
    val totalPages = 1000
    val config = makeConfig(site)

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new LinkedListHttpClient(site, totalPages),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))
    
    proxy ! Listen(self)
    proxy ! Run

    fishForMessage(3.second) {
      case Stopped(CompletedOkay, job) => 
        List(job.fetchCounters, job.responseCounters, job.mediaCounters) should equal (List(
          Map(
            FetchAttempts -> totalPages,
            FetchSucceeds -> totalPages
          ),
          Map(
            "200" -> totalPages
          ),
          Map(
            "text/html; charset=UTF-8" -> Media(totalPages, 212730)
          )
        ))
        true
      case _ => false
    }

  }

  it should "not fetch resources blocked by robots.txt" in new CrawlTest {

    val site = "http://site.net"
    val page = HtmlResponse(html.format("",
            """<a href="page1.html">link</a>
               <a href="page2.html">link</a>"""))
    val responses = Map(
              s"$site" -> page,
              s"$site/page2.html" -> page,
              s"$site/page1.html" -> page)

    val robotsCache = new MockRobotRulesCache() {
      override def allow(ua: String, reader: UriReader) = {
        Future.successful(reader.crawlUri.crawlableUri != s"$site/page2.html")
      }
    }
    val config = makeConfig(site)

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(responses),
      makeRobotRulesCache(robotsCache),
      MultiParser.default,
      new DefaultStopRule
    ))

    proxy ! Listen(self)
    proxy ! Run

    fishForMessage(1.second) {
      case Stopped(CompletedOkay, job) => 
        job.fetchCounters should equal (Map(
          FetchAttempts -> 2, // 3 - 1 blocked
          FetchSucceeds -> 2
        ))
        true
      case _ => false
    }

  }

  it should "apply the right crawl delay for the site and config" in new CrawlTest {

    val site1 = "http://site1.net"
    val site2 = "http://site2.net"
    val site3 = "http://site3.net"

    // For simplicity ignore second to millisecond conversion
    // TestDefaultRobotRulesCache already tests for this

    val site1delay = None // site 1 has no robots.txt crawl-delay directive
    val site2delay = 10   // site 2 has a delay < config default
    val confDelay =  20   // the crawl config default (in between site 2 and 3)
    val site3delay = 30   // site 3 has a delay > config default

    val responses = Map(
      site1 -> HtmlResponse(html.format("", 
              """<a href="http://site2.net">link</a>
                 <a href="http://site3.net">link</a>""")),
      site2 -> HtmlResponse(html),
      site3 -> HtmlResponse(html)
    )

    val uriFilter = new FirstMatchUriFilter(Seq(Accept("http://site.*".r)))

    val robotsCache = new MockRobotRulesCache() {
      override def getDelayFor(ua: String, reader: UriReader):Future[Option[Int]] = {
        val delay:Option[Int] = reader.crawlUri.crawlableUri match {
          case "http://site2.net" => Some(site2delay)
          case "http://site3.net" => Some(site3delay)
          case _ => None
        }
        Future.successful(delay)
      }
    }

    val config = makeConfig(site1).copy(
        uriFilter = uriFilter, 
        crawlDelayMillis = confDelay
    )

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(responses),
      makeRobotRulesCache(robotsCache),
      MultiParser.default,
      new DefaultStopRule
    ))
    
    proxy ! Listen(self)
    proxy ! Run

    var results = Map[String, Long]()

    def expectFetchScheduled = {
      fishForMessage(1.second) {
        case FetchScheduled(f, delay) => 
          results = results + (f.uri.crawlableUri -> delay)
          true
        case _ => false
      }  
    }
    expectFetchScheduled
    expectFetchScheduled
    expectFetchScheduled

    fishForMessage(1.second) {
      case Stopped(CompletedOkay, job) => true
      case _ => false
    }

    results.get(site1) should equal (Some(confDelay))
    results.get(site2) should equal (Some(confDelay))
    results.get(site3) should equal (Some(site3delay))

  }

  it should "observe the maximum crawl depth setting" in new CrawlTest {

    val site = "http://site.net"
    
    // Depth: 0     1      2     3
    // -------------------------------
    //        index
    //            \_page1
    //                  \_page2
    //                         \_page3

    val depth = 3
    val found = 4 // if including depth 0
    val totalPages = 10 // provide more pages than should be crawled
    val config = makeConfig(site).copy(maxDepth = depth)

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new LinkedListHttpClient(site, totalPages),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))
    

    proxy !Listen(self)
    proxy !Run

    fishForMessage(1.second) {
      case Stopped(CompletedOkay, job) => 
        List(job.fetchCounters, job.responseCounters, job.mediaCounters) should equal (List(
          Map(
            FetchAttempts -> found,
            FetchSucceeds -> found
          ),
          Map(
            "200" -> found
          ),
          Map(
            "text/html; charset=UTF-8" -> Media(found, 836)
          )
        ))
        true
      case _ => false
    }
    
  }

  it should "observe the maximum crawl depth even with multiple seeds" in new CrawlTest {
     
    //  Example: assuming a crawl depth limit of 1
    //
    //    a - allowed because is depth 1
    //    b - allowed because is depth 1
    //    c - not allowed because is depth 2
    //    d - allowed because even though d is depth 2 from seed 1
    //        it is depth 1 from seed 2, so permitted
    //
    //     Seed 1      Seed 2
    //      /   \        |
    //     a     b       |
    //    / \            |
    //   /   \           |
    //  c     d ---------/
    //

    val seed1 = "http://site1.net"
    val seed2 = "http://site2.net"
    val pageA = "http://site1.net/pageA"
    val pageB = "http://site1.net/pageB"
    val pageC = "http://site1.net/pageC"
    val pageD = "http://site1.net/pageD"

    val maxDepth = 1
    val found = 5 // seed1, seed2, pageA, pageB, pageD

    val responses = Map(
      seed1 -> HtmlResponse(html.format("", """<a href="pageA">link</a><a href="pageB">link</a>""")),
      seed2 -> HtmlResponse(html.format("", """<a href="http://site1.net/pageD">link</a>""")),
      pageA -> HtmlResponse(html.format("", """<a href="pageC">link</a><a href="pageD">link</a>""")),
      pageB -> HtmlResponse(html),
      pageC -> HtmlResponse(html),
      pageD -> HtmlResponse(html)
    )

    val config = makeConfig("http://site.*").copy(
      seeds = Seq(
        CrawlUri(seed1), 
        CrawlUri(seed2)
      ),
      maxDepth = maxDepth
    )

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(responses),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))

    
    proxy !Listen(self)
    proxy !Run

    fishForMessage(1.second) {
      case Stopped(CompletedOkay, job) => 
        List(job.fetchCounters, job.responseCounters, job.mediaCounters) should equal (List(
            Map(
              FetchAttempts -> found,
              FetchSucceeds -> found
            ),
            Map(
              "200" -> found
            ),
            Map(
              "text/html; charset=UTF-8" -> Media(found, 920)
            )
        ))
        true
      case _ => false
    }

  }

  it should "abort after the maximum fetch limit is exceeded" in new CrawlTest {

    val site = "http://site.net"
    val maxFetch = 5
    val config = makeConfig(site).copy(maxFetches = maxFetch)

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new LinkedListHttpClient(site, 10),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))
    
    proxy !Listen(self)
    proxy !Run

    fishForMessage(1.second) {
      case Stopped(TooManyFetches, job) => 
        job.fetchCounters should equal (Map(
          FetchAttempts -> maxFetch,
          FetchSucceeds -> maxFetch
        ))
        true
      case _ => false
    }

  }

  it should "abort when too many fetches have failed" in new CrawlTest {

    // The order of queued URIs within the same depth level is non-deterministic.
    // This can lead to test failures if there are mixed 200/404 responses for
    // a batch of pages at the same level.
    // This layout of pages prevents test failure by having all the 200 
    // successes happen first, and then have the 404s occur at depth 2.
    // That way the percentage of success to fail is not skewed in favour
    // of failure too early on.
    //
    // index
    //     \_page1
    //     |_page2
    //           \_page3 (404)
    //           |_page4 (404)
    //           |_page5 (404)
    //           ...
    //           |_page10 (404)
    //

    val index = "http://site.net"
    val page1 = "http://site.net/page1"
    val page2 = "http://site.net/page2"

    val responses = Map(
      index -> HtmlResponse(html.format("", 
                """<a href="page1">link</a>
                   <a href="page2">link</a>""")),
      page1 -> HtmlResponse(html),
      page2 -> HtmlResponse(html.format("", 
                """<a href="page3">link</a>
                   <a href="page4">link</a>
                   <a href="page5">link</a>
                   <a href="page6">link</a>
                   <a href="page7">link</a>
                   <a href="page8">link</a>
                   <a href="page9">link</a>
                   <a href="page10">link</a>""")))

    val config = makeConfig(index)

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(responses),
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))

    proxy !Listen(self)
    proxy !Run

    fishForMessage(1.second) {
      case Stopped(TooManyFetchesFailed, job) => 
        job.fetchCounters should equal (Map(
          FetchAttempts -> 10,
          FetchSucceeds -> 3,
          FetchFails -> 7
        ))
        true
      case _ => false
    }

  }

  it should "abort after the crawl timeout is exceeded" in new CrawlTest {

    val site = "http://site.net"
    val page = HtmlResponse(html.format("",
            """<a href="page1.html">link</a>
               <a href="page2.html">link</a>
               <a href="page3.html">link</a>"""))

    val responses = Map(
        s"$site" -> page,
        s"$site/page3.html" -> page,
        s"$site/page2.html" -> page,
        s"$site/page1.html" -> page
      )

    // Simulate a slow server.
    // 4 pages to crawl, each taking 100ms is a 400ms crawl.
    // The crawl timeout must therefore less than 400ms.

    val reqDelay = 100.milliseconds
    val crawlTimeout = 300
    val config = makeConfig(site).copy(crawlTimeoutMillis = crawlTimeout)

    val proxy = makeCrawlerProxy(Props(
      classOf[CrawlWorker], 
      makeJob(config),
      config,
      new InMemoryFrontier,
      new InMemoryUriCache,
      new ParrotHttpClient(responses) {
        override def responseDelay = reqDelay
      },
      mockRobotRulesCache,
      MultiParser.default,
      new DefaultStopRule
    ))
    
    proxy !Listen(self)
    proxy !Run

    fishForMessage(1.second) {
      case Stopped(CrawlTimeout, job) => true
      case _ => false
    }

  }

  class FailedCrawl extends CrawlTest {

    def testFailingCrawler(config: CrawlConfig, expectedMsg: String) = {

      val proxy = makeCrawlerProxy(Props(
        classOf[CrawlWorker], 
        makeJob(config),
        config,
        new InMemoryFrontier,
        new InMemoryUriCache,
        new ParrotHttpClient(Map.empty),
        mockRobotRulesCache,
        MultiParser.default,
        new DefaultStopRule
      ))

      proxy ! Listen(self)
      proxy ! Run

      fishForMessage(1.second) {
        case StartFailed(CrawlRejectException(msg), _) if expectedMsg == msg => true
        case _ => false
      }
    }
  }

  it should "abort when a bad CrawlConfig is discovered" in new FailedCrawl {
    // see TestCrawlConfig for fuller testing
    testFailingCrawler(
        makeConfig("http://site.net").copy(userAgent = None),
        CrawlConfig.UserAgentMissing
    )  
  }

  /**
   * Requests can fail for various reasons. These are a completely separate
   * issue to possible 500 errors that may occur on the target server side.
   *
   * - ConnectTimeout
   * - SocketTimeout
   * - MaxContentLengthExceeded - e.g. cut off for very large files
   * - InternalException - thrown for any reason
   *
   */
  // it should "handle a request internal error" in {

  // }

  // it should "handle a request timeout and continue" in {

  // }

  // it should "redirect after a 3XX response up to X times" in {

  // }
  

}