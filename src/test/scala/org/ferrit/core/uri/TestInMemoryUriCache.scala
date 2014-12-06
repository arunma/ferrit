package org.ferrit.core.uri

import org.allenai.common.testkit.UnitSpec

class TestInMemoryUriCache extends UnitSpec {

  behavior of "InMemoryUriCache"

  /**
   * Test with 2 x URIs, the second one being
   * 'normally equivalent' to the first to prove
   * that adding it to the cache will have no effect
   * if another normally equivalent URI was previously added.
   */
  it should "not store duplicate normalized URIs" in {

    val cache:UriCache = new InMemoryUriCache
    val maxSize = 10000

    (1 to maxSize).foreach(num => {

      def put(uri: CrawlUri) = {
        val newSize = cache.put(uri)
        newSize should equal (num)
        cache.contains(uri) should equal (true)
      }

      val uri = CrawlUri(s"http://site.com/page-$num")
      val uri2 = CrawlUri(s"http://site.com:80/page-$num")

      put(uri)
      put(uri)
      put(uri)
      put(uri2)
      put(uri)
      put(uri2)
    })

    cache.size should equal(maxSize)

  }

}
