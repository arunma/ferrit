package org.ferrit.core.uri

/** Stores all unvisited URIs, queued for fetching.
  */
trait Frontier {

  /** Yes you guessed it, the size of the frontier. How many items are still to be fetched?
    */
  def size: Int

  /** Add a fetch job to the frontier.
    */
  def enqueue(f: FetchJob): Int

  /** Remove a job from the frontier if there are any left.
    */
  def dequeue: Option[FetchJob]

}
