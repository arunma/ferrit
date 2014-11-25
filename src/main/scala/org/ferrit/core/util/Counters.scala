package org.ferrit.core.util

/**
 * A simple bean counter.
 */
case class Counters(counters: Map[String, Int] = Map.empty) {
  
  def increment(key: String):Counters = Counters(counters.updated(key, get(key) + 1))

  def get(key: String): Int = counters.getOrElse(key, 0)
  
  override def toString = counters.toString.replaceAll("""Map\(""", getClass.getSimpleName + "(")

}

object Counters {
  
  def apply() = new Counters(Map.empty)

}