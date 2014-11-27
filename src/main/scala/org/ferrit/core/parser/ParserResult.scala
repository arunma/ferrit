package org.ferrit.core.parser

/**
 * Contains the result of parsing a resource such as an HTML or CSS document.
 * For the purpose of crawling, the outcome is more links that will be
 * queued for fetching.
 */
trait ParserResult {
  
  /**
   * A collection of all the followable links in a document.
   * If the resource is HTML typically these will be extracted 
   * from &lt;a&gt; and &lt;img&gt; elements,
   * but may also be extracted from &lt;script&gt; and &lt;style&gt; etc.
   *
   * If the resource is a CSS file then links may be found in
   * the CSS import url('...') directive.
   */  
  def links: Set[Link]

  /** 
   * Indicates whether the HTML page contains a &lt;meta&gt; robots noindex directive.
   * Does not apply unless the resource is HTML. 
   * For example:
   *
   *     &lt;meta name="robots" content="noindex, nofollow"&gt;
   *
   * @see http://www.robotstxt.org/meta.html
   */
  def indexingDisallowed: Boolean

  /** 
   * Indicates whether the HTML page contains a &lt;meta&gt; robots nofollow directive.
   * Does not apply unless the resource is HTML. 
   * For example:
   *
   *     &lt;meta name="robots" content="noindex, nofollow"&gt;
   *
   * This indicates a directive for all links in the resource. If true, 
   * any Link extracted should also have it's nofollow directive set to true. 
   * If this directive is not found in the resource then nofollow directives
   * associated with a Link only apply to that particular Link.
   *
   * @see http://www.robotstxt.org/meta.html
   */
  def followingDisallowed: Boolean

  /**
   * How long the parsing took to complete in milliseconds
   */
  val duration: Long = 0

}