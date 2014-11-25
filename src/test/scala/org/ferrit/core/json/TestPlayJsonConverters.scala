package org.ferrit.core.json

import org.ferrit.core.crawler.CrawlConfig
import org.ferrit.core.filter.FirstMatchUriFilter
import org.ferrit.core.filter.FirstMatchUriFilter.{Accept => FAccept, Reject => FReject}
import org.ferrit.core.filter.PriorityRejectUriFilter.{Accept => PAccept, Reject => PReject}
import org.ferrit.core.json.PlayJsonImplicits.{crawlConfigReads, crawlConfigWrites, firstMatchUriFilterWrites, uriFilterReads}
import org.ferrit.core.test.CustomMatchers
import org.ferrit.core.uri.CrawlUri
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import play.api.libs.json._


class TestPlayJsonConverters extends FlatSpec with ShouldMatchers with CustomMatchers {
  
  behavior of "CrawlConfig json serialization"
  

  it should "serialize and deserialize FirstMatchUriFilter" in {
    
    val filter = new FirstMatchUriFilter(Seq(
      FReject("http://other-site.net".r),
      FAccept("http://site.net".r)
    ))
    val json = Json.stringify(Json.toJson(filter))

    val ast = Json.parse(json)
    val filter2 = Json.fromJson(ast)(uriFilterReads) match {
      case JsSuccess(f, path) => f
      case JsError(errors) => fail(s"Failed to parse $errors")
    }

    // Compare by Regex strings
    filter2.asInstanceOf[FirstMatchUriFilter].rules.map(_.toString) should equal (
      filter.rules.map(_.toString)
    )
    
  }

  it should "serialize and deserialize CrawlConfig" in {
    val site = "http://site.net"
    val config = CrawlConfig(
      id = "1234",
      userAgent = Some("Test Agent"),
      crawlerName = "Test Crawler",
      seeds = Seq(CrawlUri("http://site.net")),
      uriFilter = new FirstMatchUriFilter(Seq(
        FReject("http://other-site.net".r),
        FAccept(site.r)
      )),
      tests = Some(Seq(s"accept: $site")),
      crawlDelayMillis = 0,
      crawlTimeoutMillis = 10000,
      maxDepth = Int.MaxValue,
      maxFetches = 10000,
      maxQueueSize = 10000,
      maxRequestFails = 0.5
    )

    val json = Json.prettyPrint(Json.toJson(config))
    val ast = Json.parse(json)
    val config2 = Json.fromJson(ast)(crawlConfigReads) match {
      case JsSuccess(c, path) => c
      case JsError(errors) => fail(s"Failed to parse config $errors")
    }

    config2.crawlerName should equal (config.crawlerName)
    
  }

  it should "parse CrawlConfig from JSON string" in {

    val json = """
    {
      "id": "0f01d521-6c96-4227-bfa6-1f806c64cdf4",
      "userAgent": "WebResearch User Agent",
      "crawlerName": "Quick Crawl Localhost",  
      "seeds": ["http://localhost"],
      "uriFilter": {
        "filterClass": "org.ferrit.core.filter.FirstMatchUriFilter",
        "rules": [
          "reject: (?i).*(\\.(jpe?g|png|gif|bmp))$",
          "reject: .*/wp-includes.*",
          "reject: .*/xmlrpc.*",
          "accept: http://localhost"
        ]
      },
      "tests": [
        "accept: http://localhost"
      ],
      "maxDepth": 10,
      "maxFetches": 3,
      "maxQueueSize": 1000,
      "maxRequestFails": 0.2,
      "crawlDelayMillis": 20,
      "crawlTimeoutMillis": 600000
    }
    """

    val ast = Json.parse(json)
    val config = Json.fromJson(ast)(crawlConfigReads) match {
      case JsSuccess(c, path) => c
      case JsError(errors) => fail(s"Failed to parse config $errors")
    }

  }
 
}