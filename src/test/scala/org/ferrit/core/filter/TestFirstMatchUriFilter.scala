package org.ferrit.core.filter

import org.allenai.common.testkit.UnitSpec

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.ferrit.core.filter.FirstMatchUriFilter._

class TestFirstMatchUriFilter extends UnitSpec {

  import UriFilter.implicitConvertStringToCrawlUri

  it should "disallow on empty rules to prevent unbounded crawls" in {
    val f = new FirstMatchUriFilter(Seq())
    f.accept("http://website1.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (false)
  }

  it should "allow matching follows but disallow even if not explicitly rejected" in {

    val f = new FirstMatchUriFilter(Seq(
      Accept("http://website1.com/".r)
    ))

    f.accept("http://website1.com/") should equal (true)
    f.accept("http://website2.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (false)
  }

  it should "only allow matches by prefix" in {

    val socialMediaUri = "https://social.media.site.com?uri=http://site.net/page1"

    val f = new FirstMatchUriFilter(Seq(Accept("http://site.net/".r)))
    f.accept("http://site.net/page1") should equal (true)
    f.accept(socialMediaUri) should equal (false)

    // Unlikely this would be wanted, but strictly speaking it should match
    val uf2 = new FirstMatchUriFilter(Seq(Accept(".*http://site.net/".r)))
    uf2.accept(socialMediaUri) should equal (true)

  }

  it should "not allow when rejected or not explictly followed" in {

    val f = new FirstMatchUriFilter(Seq(
      Reject("http://website1.com/".r)
    ))

    f.accept("http://website1.com/") should equal (false)
    f.accept("http://website2.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (false)
  }

  it should "allow certain follows" in {

    val f = new FirstMatchUriFilter(Seq(
      Accept("http://website1.com/".r),
      Accept("http://sub.website1.com/".r)
    ))

    f.accept("http://website1.com/") should equal (true)
    f.accept("http://website1.com/page1") should equal (true)
    f.accept("http://website2.com/") should equal (false)
    f.accept("http://sub.website1.com/") should equal (true)
    f.accept("http://sub.website1.co.uk/") should equal (false)
  }

  /**
   * The downside of this strategy is that specific rejects
   * are going to appear at the top of the rule list which will
   * have the effect of obscuring the general simpler rules.
   */
  it should "specific reject rule overrides general follow rule" in {
    val f = new FirstMatchUriFilter(Seq(
      Reject("http://website1.com/page2".r),
      Accept("http://website1.com".r)
    ))
    f.accept("http://website1.com/") should equal (true)
    f.accept("http://website1.com/page1") should equal (true)
    f.accept("http://website1.com/page2") should equal (false)
    f.accept("http://website1.com/page2etc") should equal (false)
  }

  it should "specific follow rule overrides general reject rule" in {
    val f = new FirstMatchUriFilter(Seq(
      Accept("http://website1.com/page2".r),
      Reject("http://website1.com".r)
    ))
    f.accept("http://website1.com/") should equal (false)
    f.accept("http://website1.com/page1") should equal (false)
    f.accept("http://website1.com/page2") should equal (true)
    f.accept("http://website1.com/page2etc") should equal (true)
  }

  it should "handle all kinds of regex accept reject rules" in {

    // Accept and reject patterns require starting with ^ and/or ending with $
    // to implement "starts with" and/or "ends with" matching.
    // Certain characters in URIs needing escaping within regular expressions.
    // This is something that is easily forgotten and is usability problem !!

    val uf1 = new FirstMatchUriFilter(Seq(
      Accept("""^http://site\.com$""".r) // Exact match with ^ and $
    ))
    uf1.accept("http://site.com") should equal (true)
    uf1.accept("http://site.com/1") should equal (false)


    // Example unescaped dot treated as unintentional "any char"
    val uf2 = new FirstMatchUriFilter(Seq(
      Accept("""^http://site.com$""".r)
    ))
    uf2.accept("http://sitescom") should equal (true)

    // Must start with and end with
    // (not that useful because it misses query strings)

    val uf3 = new FirstMatchUriFilter(Seq(
      Accept("""^http://.*\.com$""".r)
    ))
    uf3.accept("http://site2.com") should equal (true)
    uf3.accept("http://website2.com") should equal (true)
    uf3.accept("http://website2.com?id=100") should equal (false)

    // Translated: "URI must start with"
    val uf4 = new FirstMatchUriFilter(Seq(
      Accept("""^http://site\.com""".r)
    ))
    uf4.accept("http://site.com/1") should equal (true)

    val uf5 = new FirstMatchUriFilter(Seq(
      Accept("^http://site2.com".r)
    ))
    uf5.accept("http://site.com?ref=http://site2.com") should equal (false)

  }

  it should "illustrate valid and invalid JPEG file extension pattern" in {

    val f = new FirstMatchUriFilter(Seq(
      Accept("""(?i)http://site.com/.*\.jpe?g$""".r)
    ))
    f.accept("http://site.com/other") should equal (false)
    f.accept("http://site.com/image.JPEG") should equal (true)
    f.accept("http://site.com/image.jpg") should equal (true)
    f.accept("http://site.com/image.jpeg") should equal (true)
    f.accept("http://site.com/image.jg") should equal (false)

    new FirstMatchUriFilter(Seq(
      Accept("""\.jpg$""".r)
    )).accept("http://site.com/.jpg/image.gif") should equal (false)

    new FirstMatchUriFilter(Seq(
      Accept("""(?i).*\.jpg$""".r)
    )).accept("http://site.com/image.JPG") should equal (true)

    new FirstMatchUriFilter(Seq(
      Accept("""\.jpg""".r)
    )).accept("http://site.com/imagejpg.gif") should equal (false)

  }


}
