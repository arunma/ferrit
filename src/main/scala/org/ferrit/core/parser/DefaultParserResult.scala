package org.ferrit.core.parser

case class DefaultParserResult(
  override val links: Set[Link],
  override val indexingDisallowed: Boolean,
  override val followingDisallowed: Boolean,
  override val duration: Long) extends ParserResult

object DefaultParserResult {
  def empty: ParserResult = DefaultParserResult(Set.empty, indexingDisallowed = false, followingDisallowed = false, 0)
}