package org.ferrit.core.util

/**
 * Life is very short.
 */
class Stopwatch {
  val started: Long = now

  var stopped: Long = _

  var isStopped: Boolean = false

  def duration: Long = {
    if (!isStopped) stopped = now
    isStopped = true
    stopped - started
  }

  private def now = System.currentTimeMillis
}