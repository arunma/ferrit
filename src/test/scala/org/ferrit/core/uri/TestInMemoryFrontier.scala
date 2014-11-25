package org.ferrit.core.uri

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers


class TestInMemoryFrontier extends FlatSpec with ShouldMatchers {
  
  behavior of "InMemoryFrontier"

  val maxSize = 100 //10000 too slow

  it should "enqueue URI" in {

    val queue = new InMemoryFrontier
    (1 to maxSize).foreach(num => {
      val uri = CrawlUri("http://website.com/page" + num)
      val fe = FetchJob(uri, 0)
      val newSize = queue.enqueue(fe)
      newSize should equal (num)
    })
    queue.size should equal (maxSize)

  }

  it should "dequeue URI" in {

    // Performance drops off a cliff after 5K items added

    val queue = new InMemoryFrontier
    val range = 1 to maxSize
    
    range.foreach(i => {
      val uri = CrawlUri("http://website.com/page" + i)
      val fe = FetchJob(uri, 0)
      val newSize = queue.enqueue(fe)
      newSize should equal (i)  
    })

    queue.size should equal (maxSize)

    // Queue is FIFO so traverse same order to dequeue
    range.foreach(i => {
      queue.dequeue match {
        case Some(fe) =>
          fe.uri should equal (
            CrawlUri("http://website.com/page" + i)
          )
        case None => fail("Failed to dequeue at index " + i)
      }
    })
  
    queue.dequeue.isEmpty should equal (true)
    
  }
}