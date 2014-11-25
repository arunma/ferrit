package org.ferrit.core.robot

/**
 * A RobotRules stores directives from a robots txt file that are assumed to apply to
 * a single user agent. To save some memory, it is expected that directives applying
 * to other user agenets would not be stored.
 *
 * The directives recognised are:
 * <ul>
 *   <li>allows</li>
 *   <li>disallows</li>
 *   <li>sitemaps</li>
 *   <li>hosts</li>
 *   <li>crawlDelay</li>
 * </ul>
 *
 * @see org.apache.nutch.protocol.RobotRules
 * @see crawler-commons project
 */
case class RobotRules(
      allows: Seq[String], 
      disallows: Seq[String], 
      sitemaps: Seq[String],
      hosts: Seq[String],
      crawlDelay: Option[Int]
  ) {
  
  /**
   * Determines if the given URI path is allowed to be visited.
   *
   * @param uriPath - the segment of the URI after
   *               the authority and before the query string.
   *               E.g. for a URI "http://site.com/path1/page1",
   *               pass in just the "/page1" segment.
   */
  def allow(uriPath: String):Boolean = {
    
    val cleanPath = 
      if (uriPath.trim.isEmpty) "/" 
      else RobotRulesParser.clean(uriPath)

    allows.find(cleanPath.startsWith) match {
      case Some(p) => true
      case None => 
        disallows.find(d => "/"==d || cleanPath.startsWith(d)) 
        match {
          case Some(p) => false
          case None => true
        }
    }
  }

}