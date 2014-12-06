package org.ferrit.core.util

/** Utility code for text formatting.
  * If this grows any bigger it is probably time to start looking
  * at using a third party library.
  */
object TextFormatter {

  def lcell(value: String, width: Int, padChar: String): String = {
    val diff = width - value.length
    lcrop(if (diff > 0) value + line(padChar, diff) else value, width)
  }

  def lcrop(value: String, num: Int): String =
    if (value.length > num) value.substring(0, num) else value

  def line(chars: String, num: Int): String =
    lcrop((0 until num).map(c => chars).mkString, num)

  def rcell(value: String, width: Int, padChar: String): String = {
    val diff = width - value.length
    rcrop(if (diff > 0) line(padChar, diff) + value else value, width)
  }

  def rcrop(value: String, num: Int): String =
    if (value.length > num) value.substring(value.length - num) else value

  def formatBytes(bytes: Long): String = humanReadableByteCount(bytes, si = true)

  /** @see http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    */
  private def humanReadableByteCount(bytes: Long, si: Boolean): String = {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) bytes + " b "
    else {
      val exp: Int = (Math.log(bytes) / Math.log(unit)).toInt
      val pre: String =
        (if (si) "kMGTPE" else "KMGTPE").charAt(exp - 1) +
          (if (si) "" else "i")
      "%.1f %sB".format(bytes / Math.pow(unit, exp), pre)
    }
  }

  // Switch to Joda PeriodFormatterBuilder?
  def formatElapsedTime(elapsedMillis: Long): String = {
    val millis = elapsedMillis % 1000
    val seconds = (elapsedMillis / 1000) % 60
    val minutes = (elapsedMillis / (1000 * 60)) % 60
    val hours = (elapsedMillis / (1000 * 60 * 60)) % 24
    val days = elapsedMillis / (1000 * 60 * 60 * 24)

    val sb = new StringBuilder
    if (days > 0) {
      sb.append(days + "d")
    }
    if (hours > 0) {
      if (sb.length > 0) sb.append(" ")
      sb.append(hours + "h")
    }
    if (minutes > 0) {
      if (sb.length > 0) sb.append(" ")
      sb.append(minutes + "m")
    }
    if (seconds > 0) {
      if (sb.length > 0) sb.append(" ")
      sb.append(seconds + "s")
    }
    if (seconds < 1 && minutes < 1 && hours < 1 && days < 1) {
      if (sb.length > 0) sb.append(" ")
      sb.append(millis + "ms")
    }
    sb.toString
  }

}
