package org.ferrit.core.robot

import org.allenai.common.testkit.UnitSpec

class TestRobotRules extends UnitSpec {

  behavior of "RobotRules"

  it should "disallow everything" in {
    val r = new RobotRules(Nil, Seq("/"), Nil, Nil, Some(0))
    r.allow("/") should equal (false)
    r.allow("/foo") should equal (false)
    r.allow("foo") should equal (false)
  }

  it should "disallow paths" in {
    val disallows = Seq("/foo", "/bar")
    val r = new RobotRules(Nil, disallows, Nil, Nil, Some(0))
    r.allow("/foo") should equal (false)
    r.allow("/foo/bar") should equal (false)
    r.allow("/foobar") should equal (false)
    r.allow("/bar") should equal (false)
    r.allow("/fo/o") should equal (true)
    r.allow("foo") should equal (true)
  }

  it should "allow paths (overriden)" in {
    val paths = Seq("/foo", "/bar")
    val r = new RobotRules(paths, paths, Nil, Nil, Some(0))
    r.allow("/foo") should equal (true)
    r.allow("/foo/bar") should equal (true)
    r.allow("/bar") should equal (true)
    r.allow("/bar/foo") should equal (true)
  }

  /**
   * Any point testing for this 'allow all' situation?
   * see: http://www.robotstxt.org/robotstxt.html
   * User-agent: *
   * Disallow:
   */
  /*it should "allow everything" in {
    val r = new RobotRules(Nil, Seq(""), Nil, Nil, 0, Some(exp))
    ...
  }*/

}
