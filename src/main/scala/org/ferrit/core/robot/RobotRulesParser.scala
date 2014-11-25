package org.ferrit.core.robot

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
 * Parses the directives in a robots.txt file applying to the given 
 * user agent, discarding directives for other user agents.
 * 
 * Standard directives parsed:
 *
 * <ul>
 *     <li>User-agent</li>
 *     <li>Disallow</li>
 * </ul>
 *
 * Non-standard directives parsed:
 *
 * <ul>
 *     <li>Allow</li>
 *     <li>Host</li>
 *     <li>Sitemap</li>
 *     <li>Crawl-delay</li>
 * </ul>
 *
 * This parser does not handle common misspellings of directives or
 * complex orderings of Allow/Disallow. An Allow directive always
 * overrides a Disallow regardless of it's position in the record.
 *
 * Implementation notes:
 * <ul>
 *     <li>Normalises line breaks to accomodate Windows/Linux formats</li>
 *     <li>Parses directives for this user agent or wildard agent record "*"</li>
 *     <li>Case-insensitive match</li>
 *     <li>Unknown directives and hash comments are silently ignored</li>
 *     <li>Performs some percent decoding, e.g. '%7e' to '~'</li>
 * </ul>
 *
 * @see    http://www.robotstxt.org/norobots-rfc.txt
 * @see    for a fuller implementation see Crawler Commons project
*          (crawlercommons.robots.SimpleRobotRulesParser)
 */
class RobotRulesParser {

  import org.ferrit.core.robot.RobotRulesParser._

  /**
   * @param userAgent - this parameter is used to select the correct
   *                    record(s) in the file to parse, namely the all
   *                    agents record (*) and any record with the same
   *                    name as this user agent.
   * @param fileContent - the file retrieved from the server
   */
  def parse(userAgent: String, fileContent: String):RobotRules = {
    
    val allows = new ListBuffer[String]
    val disallows = new ListBuffer[String]
    val sitemaps = new ListBuffer[String]
    val hosts = new ListBuffer[String]    
    var delay: String = ""
    var add = false

    val lines: Seq[String] = fileContent
        .replaceAll("\r\n", "\n") // normalize unix/windows
        .split("\n")
        .map(clean)

    lines foreach (line => {
      if (line.trim.isEmpty) add = false // end current record
      else parseLine(line) match {
        case Some((directive, v)) => 
          if ("user-agent" == directive)
            add = "*" == v || userAgent == v // enable/disable record
          else if (add) directive match {
            case "allow" => allows += v
            case "disallow" => disallows += v
            case "sitemap" => sitemaps += v
            case "host" => hosts += v
            case "crawl-delay" if delay.isEmpty => delay = v
            case _ => // ignore  
          }
        case None =>
      }
      add
    })

    val delayMillis = 
      if (delay.isEmpty) None
      else try {
        Some((delay.toDouble * 1000.0).toInt)
      } catch { case e: NumberFormatException => None }

    RobotRules(
      allows.toSeq, 
      disallows.toSeq, 
      sitemaps.toSeq, 
      hosts.toSeq,
      delayMillis
    )
  }

}

object RobotRulesParser {
  
  val LineParser: Regex = {
    val Directives = "Allow|Crawl-delay|Disallow|Host|Sitemap|User-agent"
    val value = """\s*([^\s]*)\s*.*"""
    s"(?i)($Directives)\\s*:$value".r
  }

  def parseLine(text: String):Option[(String,String)] = 
    for {
      LineParser(key, value) <- LineParser.findFirstIn(text)
    } yield (key, value)

  def clean(line: String):String =
    line
      .trim
      .toLowerCase
      .replaceAll("%7e", "~") // after lower casing

}