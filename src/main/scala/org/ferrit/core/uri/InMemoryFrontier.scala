package org.ferrit.core.uri

/**
 * A non-threadsafe implementation of Frontier based on a queue.
 * For a production like environment this would likely be replaced by something 
 * more robust such as an Akka durable message box or Apache Kafka.
 */
class InMemoryFrontier extends Frontier {
  /**
   * Performance note: when the number of queued items exceeds 10k,
   * the mutable Queue outperforms immutable Queue by an order of magnitude.
   */
  private [InMemoryFrontier] var uriQueue = scala.collection.mutable.Queue.empty[FetchJob]

  override def size: Int = uriQueue.size

  override def enqueue(f: FetchJob): Int = {
    uriQueue.enqueue(f)
    uriQueue.size
  }

  override def dequeue: Option[FetchJob] = {
    if (uriQueue.isEmpty) None
    else Some(uriQueue.dequeue)
  }
}
