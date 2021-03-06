package psp
package parser

import org.parboiled2._
import psp.parser.macros.Macros._
import psp.std._, api._, pio._, ansi._, StdShow._

// import psp.std.{ path, Path }
// import psp.std.ansi._

trait HasInputParser extends Parser {
  def input: ParserInput
  def startRule: Rule0

  def parseAllRule: Rule1[String] = rule( capture(startRule) ~ EOI )
  def parseAll(): PspParsed[String] = parseAllRule.run() match {
    case scala.util.Success(value)           => PspResult(input, Position(value.length, input), value)
    case scala.util.Failure(err: ParseError) => PspError(input, err)
    case scala.util.Failure(err)             => PspError(input, abort(s"Unknown error $err"))
  }
}

abstract class PspParser extends HasInputParser with Basic with Identifiers with Literals {

  def prevN(n: Int): String = input.sliceString(cursor - n, cursor)
  def nextN(n: Int): String = input.sliceString(cursor, cursor + n)

  /**
   * Parses all whitespace, excluding newlines. This is only
   * really useful in e.g. {} blocks, where we want to avoid
   * capturing newlines so semicolon-inference would work
   */
  def WS = rule( atomic(rep(Basic.WhitespaceChar | Literals.Comment)) )

  /**
   * Parses whitespace, including newlines.
   * This is the default for most things
   */
  def WL = rule( atomic(rep(Basic.WhitespaceChar | Literals.Comment | Basic.Newline)) )
}

class TraceScalaParser(in: ParserInput) extends ScalaParser(in) {
  val counts = scala.collection.mutable.Map[Int, Long]() withDefaultValue 0L
  private val reportFrequency = 100
  private var counted = 0L
  private def tick(): Unit = {
    counted += 1
    counts(cursor) += 1
    if (counted % reportFrequency == 0)
      println(counted / reportFrequency)
  }

  scala.sys addShutdownHook {
    println(s"\nCounted $counted rules.\n")
    counts.toList.sortBy(-_._2) foreach { case (k, v) => println("%6s  %s".format(k, v)) }
  }

  override def Type = rule( super.Type ~ run(tick()) )
}
