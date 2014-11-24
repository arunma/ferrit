package org.ferrit.core.crawler

import akka.actor.{ActorSystem, Actor, ActorRef, Props}
import akka.testkit.{TestKit, ImplicitSender}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatest.matchers.ShouldMatchers
import org.ferrit.core.crawler.CrawlWorker.Stopped
import org.ferrit.core.filter.FirstMatchUriFilter
import org.ferrit.core.filter.FirstMatchUriFilter.Accept
import org.ferrit.core.http.{HttpClient, Request, Response}
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.robot.{RobotRulesCache, DefaultRobotRulesCache, RobotRulesCacheActor}
import org.ferrit.core.test.{LinkedListHttpClient, FakeHttpClient}
import org.ferrit.core.test.FakeHttpClient.HtmlResponse
import org.ferrit.core.uri.CrawlUri
import org.ferrit.core.util.{Counters, UniqueId}


class TestCrawlerManager extends FlatSpec with ShouldMatchers with BeforeAndAfterAll {
  
  behavior of "CrawlerManager"

  import SpiderManager._

  implicit val system = ActorSystem("test")
  implicit val execContext = system.dispatcher
  val node = "localhost"
  val userAgent = "Test Agent"

  def logger = system.actorOf(Props[CrawlLog])

  override def afterAll():Unit = system.shutdown()

  class ManagerTest extends TestKit(system) with ImplicitSender {

    /**
     * All crawlers use the same robot rules cache 
     */
    def robotRulesCache(httpClient: HttpClient) = {
      system.actorOf(Props(
        classOf[RobotRulesCacheActor], 
        new DefaultRobotRulesCache(httpClient)
      ))
    }  

    def makeManager(maxCrawlers: Int, httpClient: HttpClient):ActorRef = 
      system.actorOf(Props(
        classOf[SpiderManager],
        node,
        userAgent,
        maxCrawlers, 
        httpClient,
        robotRulesCache(httpClient)
      ))

  }

  def makeConfig(uri: String) = CrawlConfig(
      id = UniqueId.next,
      userAgent = None,
      crawlerName = "Test Crawler " + scala.util.Random.nextInt(10000),
      seeds = Seq(CrawlUri(uri)),
      uriFilter = new FirstMatchUriFilter(Seq(Accept(uri.r))),
      tests = None,
      crawlDelayMillis = 30, // crawls complete too quickly if 0
      crawlTimeoutMillis = 20000,
      maxDepth = Int.MaxValue,
      maxFetches = 10000,
      maxQueueSize = 10000,
      maxRequestFails = 0.5
    )

  val askTimeout = new Timeout(1.second)

  val NoLogger = Nil // or Some(logger)


  it should "not accept new job with duplicate crawler configuration" in new ManagerTest {
    
    val site = "http://site.net"
    val manager = makeManager(10, new LinkedListHttpClient(site, 10))
    val config = makeConfig(site)

    manager ! StartJob(config, NoLogger)
    fishForMessage(1.second) { 
      case c: CrawlJob => true 
    }

    manager ! StartJob(config, NoLogger)
    fishForMessage(1.second) { 
      case JobStartFailed(CrawlRejectException(SpiderManager.crawlerExists)) => true
    }
  }

  it should "not accept new job when max crawlers exceeded" in new ManagerTest {

    val maxCrawlers = 1
    val site = "http://site.net"
    val manager = makeManager(maxCrawlers, new LinkedListHttpClient(site, 10))

    val config1 = makeConfig(site)
    val config2 = makeConfig(site).copy(crawlerName = "Another Crawler")
    
    manager ! StartJob(config1, NoLogger)
    fishForMessage(1.second) { 
      case c: CrawlJob => true 
    }

    manager ! StartJob(config2, NoLogger)
    fishForMessage(1.second) { 
      case JobStartFailed(CrawlRejectException(SpiderManager.tooManyCrawlers)) => true
    }

  }

  it should "not accept new job for a bad crawler configuration" in new ManagerTest {

    val manager = makeManager(10, new LinkedListHttpClient("etc", 10))
    val config = makeConfig("etc").copy(userAgent = Some(" "))

    manager ! StartJob(config, NoLogger)
    fishForMessage(1.second) { 
      case JobStartFailed(CrawlRejectException(CrawlConfig.UserAgentMissing)) => true 
    }

  }

  it should "provide information about running crawlers" in new ManagerTest {
    
    val site = "http://site.net"
    val manager = makeManager(10, new LinkedListHttpClient(site, 50))
    val config = makeConfig(site)

    manager ! StartJob(config, NoLogger)

    val job = {
      var opt: Option[CrawlJob] = None
      fishForMessage(1.second) { 
        case c: CrawlJob => opt = Some(c); true
      }
      opt match {
        case None => fail("new CrawlJob not created or found?")
        case Some(job) => job
      }
    }

    manager ! JobsQuery()
    fishForMessage(1.second) {
      case JobsInfo(Seq(CrawlJob(crawlerId, _, id, _,_,_,_,_,_,_,_,_,_,_,_,_ )))
        if (config.id == crawlerId && job.jobId == id) => true
    }

  }

  //
  // The number of pages and crawl delay needs to be such that the crawler
  // does not complete by itself before there is a chance to issue a StopCrawl
  // and check that the job was stopped and removed.
  //

  it should "stop a running crawler" in new ManagerTest {
    
    val site = "http://site.net"
    val manager = makeManager(10, new LinkedListHttpClient(site, 50))
    val config = makeConfig(site)

    manager ! StartJob(config, NoLogger)

    val job = {
      var opt: Option[CrawlJob] = None
      fishForMessage(1.second) { 
        case c: CrawlJob => opt = Some(c); true
      }
      opt match {
        case None => fail("new CrawlJob not created or found?")
        case Some(job) => job
      }
    }
  
    manager ! StopJob(job.jobId)
    fishForMessage(1.second) {
       case StopAccepted(Seq(id)) if (job.jobId == id) => true
    }
    manager ! JobsQuery()
    fishForMessage(1.second) {
       case JobsInfo(jobs) if (jobs.isEmpty) => true
       case other => 
        manager ! JobsQuery() // keep asking until timeout
        false
    }
    
  }

  it should "stop all running crawlers" in new ManagerTest {

    val maxJobs = 10
    val site = "http://site.net"  
    val manager = makeManager(maxJobs, new LinkedListHttpClient(site, 50))

    (1 to maxJobs).foreach({i =>
      manager ! StartJob(makeConfig(site), NoLogger)  
    })
    
    val jobsInfo = Await.result(
      manager.ask(JobsQuery())(askTimeout).mapTo[JobsInfo], 1.seconds
    )
    jobsInfo.jobs.size should equal (maxJobs)

    manager ! StopAllJobs()

    manager ! JobsQuery()
    fishForMessage(1.second) {
       case JobsInfo(jobs) if (jobs.isEmpty) => true
       case other => 
        manager ! JobsQuery() // keep asking until timeout
        false
    }

  }

    
  // Creates N crawlers each searching for P pages.
  // The HTML template returns a page with P links which
  // should result in a crawl of P pages because each page
  // is returning the same HTML.

  it should "run concurrent crawls" in new ManagerTest {

    // !!! CAREFUL with these settings !!!
    // -----------------------------------
    // Set totalPages/maxCrawlers too high and you can
    // max out all cores on a 4 core i7 at 100%

    // Takes 8 seconds:
    //   maxCrawlers = 20
    //   totalPages = 200
    //   TOTAL: 4000 pages

    // Takes 2 seconds
    //   maxCrawlers = 200
    //   totalPages = 20
    //   TOTAL: 4000 pages

    // Possible reasons why few crawlers many pages takes longer:
    // * Slow queue: fixed by changing to mutable
    // * Request delay: fixed by reducing to 0
    //
    // * Pages with many links are MUCH larger and slower to parse
    //   (parsing HTML takes longer and more links need extracting)
    // * Crawlers are serial, they do not do parallel fetches on same site,
    //   whereas multiple crawlers can run concurrently.
    //   The single threaded nature of the crawler can be seen when running
    //   just one crawl job - the load is bound to one CPU.
    // * URL seen queue is larger
    
    val maxCrawlers = 10
    val totalPages = 10

    // Average page size is more like 20-30kb with less than 100 links
    // News sites seem to have larger pages.

    // The current HTML generation algorithm does not scale correctly
    // #Links  HTML Bytes
    // ------------------
    //    10        438
    //   100       3137  2.6 Kb
    //   200       6236  6.2 Kb
    //   500      15536  15 Kb
    //  1000      31037  31 Kb
    
    // Pre-generate HTML before running to avoid cost during test.
    // Takes about 500ms for 6000 pages.
    
    val total = maxCrawlers * totalPages
    val pages:Array[String] = (0 to total).map({i => makeHtml(totalPages)}).toArray

    val httpClient = new FakeHttpClient {
      override val _ec = execContext
      override def handleRequest(request: Request):Response = {
        val idx = scala.util.Random.nextInt(total)
        val html = pages(idx) // serve pre-generated page
        val pr = HtmlResponse(html)
        pr.toResponse(request)
      }
    }

    def runCrawl(site: String):Future[Boolean] = {

      val p = Promise[Boolean]()

      val config = CrawlConfig(
        id = UniqueId.next,
        userAgent = None,
        crawlerName = "Test Crawler " + scala.util.Random.nextInt(10000),
        seeds = Seq(CrawlUri(site)),
        uriFilter = new FirstMatchUriFilter(Seq(Accept(site.r))),
        tests = None,
        crawlDelayMillis = 0, // <--- controls speed of test
        crawlTimeoutMillis = 20000,
        maxDepth = Int.MaxValue,
        maxFetches = 20000,
        maxQueueSize = 20000,
        maxRequestFails = 0.5
      )

      val listener = system.actorOf(Props(new Actor {
        def receive = {
          case Stopped(CompletedOkay, job) => 
            val expectedResult = 
              job.fetchCounters == Map(
                "FetchAttempts" -> totalPages,
                "FetchSucceeds" -> totalPages
              )
            p.success(expectedResult)
        }
      }))

      manager ! StartJob(config, Seq(listener))
      
      p.future
    }

    // Creates single manager for all crawlers

    val manager = system.actorOf(Props(
      classOf[SpiderManager],
      node,
      userAgent,
      maxCrawlers, 
      httpClient,
      robotRulesCache(httpClient)
    ))

    // Submits N crawl jobs.
    // Things will now start getting hot around here ...

    val start = System.currentTimeMillis

    val future = Future.sequence(
      (1 to maxCrawlers).map(i => runCrawl(s"http://site$i.net"))
    )

    // A result of Vector(true, true ...) means all crawlers completed okay
    val result = Await.result(future, 16.seconds)
    result.forall(_ == true) should equal (true)
  
    val info = Await.result(
      manager.ask(JobsQuery())(askTimeout).mapTo[JobsInfo], 
      16.seconds
    )

    val end = System.currentTimeMillis

    info.jobs.size should equal (0) // all finished jobs removed

  }

  def makeHtml(links: Int) = 
      ("""
      |<html>
      |<head>
      |<title>Test Page</title>
      |</head>
      |<body>
      |<h1>Test Page</h1>
      |<p>The section below contains a batch of auto-generated anchors:</p>
      """ + {
        
        val range = 0 until (links-1)
        scala.util.Random.shuffle(
          range.map(i => s"<a href='page$i'>link text</a>")
        ).mkString

      } + """
      |</body>
      |</html>
      |""").stripMargin

}
