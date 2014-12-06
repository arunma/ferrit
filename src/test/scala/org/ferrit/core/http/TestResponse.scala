package org.ferrit.core.http

import org.allenai.common.testkit.UnitSpec

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class TestResponse extends UnitSpec {

  behavior of "Response"

  it should "return first header" in {

    val r = DefaultResponse(
      200,
      Map(
        "Content-Type" -> Seq("text/html"),
        "Duplicate-Headers" -> Seq("dupe1", "dupe2")
      ),
      Array.empty[Byte],
      Stats.empty,
      null // naughty! should replace with mock Response
    )

    r.firstHeader("Content-Type") should equal (Some("text/html"))
    r.firstHeader("No-Such-Header") should equal (None)
    r.firstHeader("Duplicate-Headers") should equal (Some("dupe1"))

  }

}
