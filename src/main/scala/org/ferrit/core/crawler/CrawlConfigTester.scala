package org.ferrit.core.crawler

import org.ferrit.core.uri.CrawlUri
import org.ferrit.core.util.KeyValueParser

/**
 * A CrawlConfigTester is a utility to aid crawler configuration in a user interface.
 *
 * It contains a collection of tests and applies these to the seeds and UriFilter rules
 * of a CrawlConfig before persisting it and also applies these tests prior to starting 
 * a crawl job.
 *
 * Applying tests to the seeds and UriFilter using pre-determined cases means that the
 * likelihood of crawling unwanted content is reduced. The use of tests also makes it
 * possible to 'refactor' the seeds and UriFilter rules without breaking out of the
 * expected frontier.
 *
 * If one or more of the tests fail then the CrawlConfig should not be persisted and
 * a crawler job using the config should not be allowed to start.
 *
 * Test syntax:
 *
 * <code>
 * should [accept|reject] url-regex-pattern
 * </code>
 *
 * An configuration example with the following seeds and UriFilter rules:
 *
 * Seeds:
 * <ul>
 * <li>www.website.com </li>
 * </ul>
 *
 * PriorityRejectUriFilter rules:
 * <ul>
 * <li>accept: http://www.website.com/ <li/>
 * <li>reject: .*(rss|login|/?option=com_banners|/#) <li/>
 * </ul>
 *
 * The following test cases ensure that the above seeds are accepted and
 * not blocked the PriorityRejectUriFilter rules and that any rules
 * containing rss, login or a banner option parameter are rejected.
 *
 * <code>
 * should accept: http://www.website.com/somepage <br/>
 * should reject: http://www.website.com/rss/ <br/>
 * should reject: http://www.website.com/index.php?option=com_bannersmanager&task=click&bid=1115 <br/>
 * </code>
 *
 */
object CrawlConfigTester {
  val TestDirectives = Seq("should accept", "should reject")
  val Passed = "passed"

  def testConfig(config: CrawlConfig): Results = {
    val filter = config.uriFilter

    def testUri(uri: CrawlUri, shouldAccept: Boolean) = {
      val passed = if (shouldAccept) filter.accept(uri) else !filter.accept(uri)
      val message = if (passed) Passed else filter.explain(uri)
      Result(uri, passed, message)
    }

    val seedResults = config.seeds.map(testUri(_, shouldAccept = true))

    def bindFn(key: String, value: String) = key match {
      case "should accept" => testUri(CrawlUri(value), shouldAccept = true)
      case "should reject" => testUri(CrawlUri(value), shouldAccept = false)
    }

    val testResults = config.tests match {
      case None => Nil
      case Some(tests) => KeyValueParser.parse(TestDirectives, tests, bindFn)
    }

    val allPassed = (seedResults ++ testResults).forall(_.passed)
    Results(allPassed, seedResults, testResults)
  }

  case class Results(
    allPassed: Boolean,
    seedResults: Seq[Result],
    testResults: Seq[Result])

  case class Result(
    uri: CrawlUri,
    passed: Boolean,
    message: String)
}
