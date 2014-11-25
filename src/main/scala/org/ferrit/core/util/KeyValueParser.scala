package org.ferrit.core.util


object KeyValueParser {

  final val Ls:String = System.getProperty("line.separator")
  private val KeyVal = """(?i)^([a-z\s-]+):\s*(.*)""".r

  /**
   * Parse a sequence of lines where duplicate keys are allowed.
   * Example:
   *
   *   accept: http://site.net
   *   reject: http://other.site.net
   *
   */
  def parse[T](
    directives:Seq[String], 
    lines: Seq[String], 
    bindFn: (String,String) => T):Seq[T] = {
    
    val Directives = directives.mkString("|")
    
    lines
      .filterNot(l => l.trim.isEmpty || l.startsWith("#"))
      .map({line =>
      KeyVal.findFirstMatchIn(line) match {
        case Some(m) if m.group(1) != null && m.group(1).matches(Directives) => 
            bindFn(m.group(1), m.group(2).trim)
        case m => 
            throw new IllegalArgumentException(
              s"Unrecognised directive on line [$line]. " +
              "Directives should be one of [%s]".format(directives.mkString(","))
            )
      }
    })

  }

}