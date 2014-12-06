package org.ferrit.core.util

import org.allenai.common.testkit.UnitSpec

class TestKeyValueParser extends UnitSpec {

  case class KeyVal(key: String, value: String)

  val fromPairFn: (String,String) => KeyVal = (k:String, v:String) => KeyVal(k,v)


  behavior of "KeyValueParser"

  it should "parse sequence of lines allowing for repeating keys" in {

    val tests = """
      |
      |# Comments are ignored
      |# by the key value parser
      |
      |accept: http://site1
      |accept: http://site2
      |
      |reject:   http://site3
      |reject:  http://site4
      |
      """.stripMargin

    val pairs:Seq[KeyVal] = KeyValueParser.parse(
      Seq("accept", "reject"),
      tests.split(KeyValueParser.Ls).toSeq,
      fromPairFn
    )

    pairs should equal (Seq(
      KeyVal("accept", "http://site1"),
      KeyVal("accept", "http://site2"),
      KeyVal("reject", "http://site3"),
      KeyVal("reject", "http://site4")
    ))

  }

  it should "throw on bad input" in {

    // Parsing could be made lenient regarding whitespace but
    // is deliberately strict to prevent bad formatting styles

    intercept[IllegalArgumentException] {
      KeyValueParser.parse(
        Seq("accept", "reject"),
        Seq(" accept: space before directive disallowed"),
        fromPairFn
      )
    }

    intercept[IllegalArgumentException] {
      KeyValueParser.parse(
        Seq("accept", "reject"),
        Seq("reject :  space after directive disallowed"),
        fromPairFn
      )
    }

    intercept[IllegalArgumentException] {
      KeyValueParser.parse(
        Seq("accept", "reject"),
        Seq("unknown key with no colon or value"),
        fromPairFn
      )
    }

    intercept[IllegalArgumentException] {
      KeyValueParser.parse(
        Seq("accept", "reject"),
        Seq("""|unknown_key: and value
           |accept: http://site""".stripMargin),
        fromPairFn
      )
    }

    intercept[IllegalArgumentException] {
      KeyValueParser.parse(
        Seq("key_with_no_value"),
        Seq("key_with_no_value:"),
        fromPairFn
      )
    }

  }

}
