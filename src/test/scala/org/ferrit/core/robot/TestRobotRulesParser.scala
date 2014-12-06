package org.ferrit.core.robot

import org.allenai.common.testkit.UnitSpec

import org.ferrit.core.robot.RobotRulesParser.LineParser

class TestRobotRulesParser extends UnitSpec {

    behavior of "RobotRulesParser"

    val parser = new RobotRulesParser
    val EmptyRules = RobotRules(Nil, Nil, Nil, Nil, None)
    def parseLine(text: String) = RobotRulesParser.parseLine(text)


    it should "parse known directives" in {
      parseLine ("user-agent: *") should equal (Some("user-agent", "*"))
      parseLine ("user-agent: custom") should equal (Some("user-agent", "custom"))
      parseLine ("user-agent:custom") should equal (Some("user-agent", "custom"))
      parseLine ("user-agent:custom  ") should equal (Some("user-agent", "custom"))
      parseLine ("disallow: /page") should equal (Some("disallow", "/page"))
      parseLine ("disallow:/page") should equal (Some("disallow", "/page"))
      parseLine ("allow: /page") should equal (Some("allow", "/page"))
      parseLine ("allow:/page") should equal (Some("allow", "/page"))
      parseLine ("crawl-delay: 999") should equal (Some("crawl-delay", "999"))
      parseLine ("crawl-delay:999") should equal (Some("crawl-delay", "999"))
      parseLine ("host: www.site.com") should equal (Some("host", "www.site.com"))
      parseLine ("sitemap: /sitemap.xml") should equal (Some("sitemap", "/sitemap.xml"))
      // The regex parsing the directive value cannot handle spaces in words
      //parseLine ("disallow: custom agent") should equal (Some("disallow", "custom agent"))
    }

    it should "ignore unknown directives" in {
      parseLine ("Ignore: /page") should equal (None)
      parseLine ("Disallowed: /page") should equal (None)
    }

    it should "ignore comments" in {
      parseLine ("# a comment") should equal (None)
      parseLine ("#") should equal (None)
      parseLine ("# ") should equal (None)
    }


    it should "parse all types of directive" in {
      val rules = """
        |User-agent: agent
        |Sitemap: http://site.com/sitemap.xml
        |Disallow: /abc
        |Allow: /gh
        |Host: www.site.com
        |"""
        .stripMargin
      parser.parse("agent", rules) should equal (
        RobotRules(
          Seq("/gh"),
          Seq("/abc"),
          Seq("http://site.com/sitemap.xml"),
          Seq("www.site.com"),
          None
        )
      )
    }

    it should "not parse rules for different user agent" in {
      val rules = """
        |User-agent: custom_agent
        |Disallow: a
        |"""
        .stripMargin
      parser.parse("custom_agent2", rules) should equal (EmptyRules)
      parser.parse("other_agent", rules) should equal (EmptyRules)
    }

    it should "parse crawl delays (integer)" in {
      val rules = """
        |User-agent: custom_agent
        |Crawl-delay: 2
        |Disallow: a
        |"""
        .stripMargin
      parser.parse("custom_agent", rules) should equal (
        RobotRules(Nil, Seq("a"), Nil, Nil, Some(2000))
      )
    }

    it should "parse crawl delays (double)" in {
      // In case webmaster types 1.5 seconds (see crawler-commons)
      val rules = """
        |User-agent: custom_agent
        |Disallow: a
        |Crawl-delay: 1.5
        |"""
        .stripMargin
      parser.parse("custom_agent", rules) should equal (
        RobotRules(Nil, Seq("a"), Nil, Nil, Some(1500))
      )
    }

    it should "parse two records (wildcard agent first)" in {

      val rules = """
        |User-agent: *
        |Disallow: /abc
        |
        |User-agent: other
        |Disallow: /should_not_be_parsed!
        |
        |User-agent: custom_agent
        |Sitemap: http://site.com/sitemap.xml
        |Disallow: /abc
        |Disallow: /def
        |Allow: /gh
        |Host: www.site.com
        |
        |""".stripMargin

      parser.parse("custom_agent", rules) should equal (
        RobotRules(
          Seq("/gh"),
          Seq("/abc","/abc", "/def"),
          Seq("http://site.com/sitemap.xml"),
          Seq("www.site.com"),
          None
        )
      )
    }

    it should "parse two records (wildcard agent last)" in {

      val rules = """
        |User-agent: custom_agent
        |Sitemap: http://site.com/sitemap.xml
        |Disallow: /abc
        |Disallow: /def
        |Allow: /gh
        |Host: www.site.com
        |
        |User-agent: other_agent
        |Disallow: /should_not_be_parsed!
        |
        |User-agent: *
        |Disallow: /abc
        |Disallow: /jkl
        |
        |""".stripMargin

      parser.parse("custom_agent", rules) should equal (
        RobotRules(
          Seq("/gh"),
          Seq("/abc", "/def","/abc", "/jkl"),
          Seq("http://site.com/sitemap.xml"),
          Seq("www.site.com"),
          None
        )
      )
    }

    it should "parse two records (other agent first)" in {

      val rules = """
        |User-agent: other_agent
        |Disallow: /should_not_be_parsed!
        |
        |User-agent: custom_agent
        |Sitemap: http://site.com/sitemap.xml
        |Disallow: /abc
        |Disallow: /def
        |Allow: /gh
        |Host: www.site.com
        |
        |User-agent: *
        |Disallow: /abc
        |Disallow: /jkl
        |
        |""".stripMargin

      parser.parse("custom_agent", rules) should equal (
        RobotRules(
          Seq("/gh"),
          Seq("/abc", "/def","/abc", "/jkl"),
          Seq("http://site.com/sitemap.xml"),
          Seq("www.site.com"),
          None
        )
      )
    }

    it should "handle some character decoding" in {
      val rules = """
        |User-agent: *
        |Disallow: %7e/page
        |"""
        .stripMargin
      parser.parse("agent", rules) should equal (
        RobotRules(Nil, Seq("~/page"), Nil, Nil, None)
      )
    }

    // Functionally the Allow directive is processed in a manner
    // similar to Google's implementation.
    //
    // Some major crawlers support an Allow directive which can counteract
    // a following Disallow directive. This is useful when one tells
    // robots to avoid an entire directory but still wants some HTML documents
    // in that directory crawled and indexed. While by standard implementation
    // the first matching robots.txt pattern always wins,
    // Google's implementation differs in that Allow patterns with equal or more
    // characters in the directive path win over a matching Disallow pattern.
    //
    // @see http://en.wikipedia.org/wiki/Robots_exclusion_standard

    it should "let allow override disallow - single record" in {

      def assertAllows(rr: RobotRules) = {
        rr.allow("/foo/bar") should equal (true)
        rr.allow("/foo/baz") should equal (false)
        rr.allow("/foo") should equal (false)
      }

      assertAllows(parser.parse("agent",
        """
        |User-agent: *
        |Disallow: /foo
        |Allow: /foo/bar
        |"""
        .stripMargin
      ))

      assertAllows(parser.parse("agent",
        """
        |User-agent: *
        |Allow: /foo/bar
        |Disallow: /foo
        |"""
        .stripMargin
      ))

      assertAllows(parser.parse("agent",
        """
        |User-agent: agent
        |Disallow: /foo
        |Allow: /foo/bar
        |"""
        .stripMargin
      ))

      assertAllows(parser.parse("agent",
        """
        |User-agent: agent
        |Allow: /foo/bar
        |Disallow: /foo
        |"""
        .stripMargin
      ))

    }

    it should "let allow override disallow - multiple records" in {

      def assertAllows(rr: RobotRules) = {
        rr.allow("/foo/bar") should equal (true)
        rr.allow("/foo/baz") should equal (false)
        rr.allow("/foo") should equal (false)
      }

      assertAllows(parser.parse("agent",
        """
        |User-agent: *
        |Allow: /foo/bar
        |
        |User-agent: agent
        |Disallow: /foo
        |"""
        .stripMargin
      ))

      assertAllows(parser.parse("agent",
        """
        |User-agent: *
        |Disallow: /foo
        |
        |User-agent: agent
        |Allow: /foo/bar
        |"""
        .stripMargin
      ))

    }

}
