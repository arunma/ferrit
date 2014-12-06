package org.ferrit.core.robot

import org.allenai.common.testkit.UnitSpec

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.BeforeAndAfterAll
import org.ferrit.core.http.Response
import org.ferrit.core.test.{ParrotHttpClient, PartResponse}
import org.ferrit.core.test.FakeHttpClient._
import org.ferrit.core.uri.CrawlUri

class TestRobotRulesCacheActor extends UnitSpec with BeforeAndAfterAll {

  import RobotRulesCacheActor._

  implicit val system = ActorSystem("test")
  implicit val execContext = system.dispatcher
  implicit val timeout = new Timeout(1.seconds)

  val waitTime = 100.milliseconds
  val userAgent = "space agent"
  val site1 = "http://site1.net"
  val site2 = "http://site2.net"
  val robotsTxt1 = """|User-agent: *
                      |disallow: /private
                      |crawl-delay: 2""".stripMargin
  val robotsTxt2 = """|User-agent: *
                      |disallow: /hidden""".stripMargin

  val responses = Map(
    s"$site1/robots.txt" -> TextResponse(robotsTxt1),
    s"$site2/robots.txt" -> TextResponse(robotsTxt2)
  )


  override def afterAll() = system.shutdown()

  class RulesTest extends TestKit(system) with ImplicitSender {

    def getCacheActor(responses: Map[String, PartResponse]) = {
      val httpClient = new ParrotHttpClient(responses)
      val cache = new DefaultRobotRulesCache(httpClient)
      system.actorOf(Props(classOf[RobotRulesCacheActor], cache))
    }
  }


  behavior of "RobotRulesCacheActor"


  it should "allow URI access when asked" in new RulesTest {

    val cache = getCacheActor(responses)

    Await.result(
        cache.ask(Allow(userAgent, CrawlUri(s"$site1/page").reader)),
        waitTime
    ) should equal (true)

    Await.result(
        cache.ask(Allow(userAgent, CrawlUri(s"$site2/private").reader)),
        waitTime
    ) should equal (true)

    Await.result(
        cache.ask(Allow(userAgent, CrawlUri(s"$site1/private").reader)),
        waitTime
    ) should equal (false)

    Await.result(
        cache.ask(Allow(userAgent, CrawlUri(s"$site2/hidden").reader)),
        waitTime
    ) should equal (false)

  }

  it should "determine crawl delay for site when asked" in new RulesTest {

    val cache = getCacheActor(responses)

    Await.result(
        cache.ask(DelayFor(userAgent, CrawlUri(s"$site1/page").reader)),
        waitTime
    ) should equal (Some(2000)) // 2s converted to 2000ms

    Await.result(
        cache.ask(DelayFor(userAgent, CrawlUri(s"$site2/page").reader)),
        waitTime
    ) should equal (None) // no crawl-delay defined

  }

}
