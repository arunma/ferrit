package org.ferrit.core.test

import org.scalatest.Matchers

trait CustomMatchers extends Matchers {

  /**
   * Augments ScalaTest intercept[Throwable]
   * with a variation that allows for equality comparison.
   * This applies to custom Throwable types that are case classes
   * or override equals.
   * Usage: for finer-grained checking on the actual
   * throwable message returned, useful in situations where a
   * function may throw Throwable with different messages.
   */
  def intercept(t: Throwable)(fn: =>Unit) {
    try { fn } catch {
      case t2: Throwable => t2 should equal (t)
    }
  }

}
