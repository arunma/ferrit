package org.ferrit.core.filter

import org.allenai.common.testkit.UnitSpec

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.ferrit.core.filter.PriorityRejectUriFilter._

class TestPriorityRejectUriFilter extends UnitSpec {

  import UriFilter.implicitConvertStringToCrawlUri

  it should "reject on empty rules to prevent unbounded crawls" in {
    val f = new PriorityRejectUriFilter(Seq())
    f.accept("http://website1.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (false)
  }

  it should "check for accepts but reject even when not explicitly rejected" in {
    val f = new PriorityRejectUriFilter(Seq(
      Accept("http://website1.com/".r)
    ))
    f.accept("http://website1.com/") should equal (true)
    f.accept("http://website2.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (false)
  }

  it should "reject when rejected or not explictly accepted" in {

    val f = new PriorityRejectUriFilter(
      Seq(Reject("http://website1.com/".r))
    )

    f.accept("http://website1.com/") should equal (false)
    f.accept("http://website2.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (false)
  }

  it should "reject over accept in trailing slash edge case" in {
    val f = new PriorityRejectUriFilter(Seq(
      Accept("http://website1.com/".r),
      Reject("http://website1.com".r)
    ))
    f.accept("http://website1.com/") should equal (false)
    f.accept("http://website1.com") should equal (false)
  }

  it should "accept over reject in trailing slash edge case" in {
    val f = new PriorityRejectUriFilter(Seq(
      Accept("http://website1.com".r),
      Reject("http://website1.com/".r)
    ))
    f.accept("http://website1.com") should equal (true)
    f.accept("http://website1.com/") should equal (false)
  }

  it should "allow certain accepts" in {
    val f = new PriorityRejectUriFilter(Seq(
      Accept("http://website1.com/".r),
      Accept("http://sub.website1.com/".r)
    ))
    f.accept("http://website1.com/") should equal (true)
    f.accept("http://website1.com/page1") should equal (true)
    f.accept("http://website2.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (true)
  }

  it should "specific reject rule overrides general accept rule" in {
    val f = new PriorityRejectUriFilter(Seq(
      Accept("http://website1.com".r),
      Reject("http://website1.com/page2".r)
    ))
    f.accept("http://website1.com/") should equal (true)
    f.accept("http://website1.com/page1") should equal (true)
    f.accept("http://website1.com/page2") should equal (false)
    f.accept("http://website1.com/page2etc") should equal (false)
  }

  it should "reject when there are identical accepts and rejects" in {
    val f = new PriorityRejectUriFilter(Seq(
      Accept("http://website1.com/".r),
      Accept("http://website2.com/".r),
      Reject("http://website1.com/".r)
    ))
    f.accept("http://website1.com/") should equal (false)
    f.accept("http://website1.com/page1") should equal (false)
    f.accept("http://website2.com/") should equal (true)
  }

  it should "only allow matches by prefix" in {

    val socialMediaUri = "https://social.media.site.com?uri=http://site.net/page1"

    val f = new PriorityRejectUriFilter(Seq(Accept("http://site.net/".r)))
    f.accept("http://site.net/page1") should equal (true)
    f.accept(socialMediaUri) should equal (false)

    // Unlikely this would be wanted, but strictly speaking it should match
    val f2 = new PriorityRejectUriFilter(Seq(Accept(".*http://site.net/".r)))
    f2.accept(socialMediaUri) should equal (true)

  }

  /**
   * Because rejects take priority over accepts it is not possible with this filter
   * to mix a more general reject with a very specific accept. In this scenario the
   * FirstMatchUriFilter would be preferable.
   */
  it should "specific accept rule overrides general reject rule" in {

    val f = new PriorityRejectUriFilter(Seq(
        Accept("http://website1.com/page2".r),
        Reject("http://website1.com".r)
    ))
    f.accept("http://website1.com/") should equal (false)
    f.accept("http://website1.com/page1") should equal (false)

    // Proves that the accept is not allowed
    f.accept("http://website1.com/page2") should equal (false)
    f.accept("http://website1.com/page2etc") should equal (false)

  }

}
