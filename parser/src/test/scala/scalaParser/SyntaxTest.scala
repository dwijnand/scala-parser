package scalaParser

import org.parboiled2.ParseError
import psp.std.{ assert => _, _}
import psp.std.api._
import scala.util.{Failure, Success}
import psp.std.ansi._
import scala.sys.process.Process
import java.nio.file.NoSuchFileException

sealed trait Result { def ansi: String }
final case object Pass extends Result { def ansi = "ok".green.to_s }
final case object Fail extends Result { def ansi = "failed".red.to_s }
final case object Skip extends Result { def ansi = "skip".yellow.to_s }

object SyntaxTest {
  import Predef.{ augmentString => _, wrapString => _, ArrowAssoc => _, _ }

  private def tryOpt[A](body: => Option[A]): Option[A] = Try(body) | None

  // A unicode escape without any quote characters on the same line.
  val unicodeRegex = """^[^"']*[\\][u]\d{4}[^"']*$"""

  def maxFileLen   = 100
  def maxFileFmt   = "%-" + maxFileLen + "s"
  def scalaSources = "."

  def scalaPaths(root: Path): Seq[Path] = Process(Seq("find", s"$root/", "-type", "f", "-name", "*.scala", "-print")).lines map (x => path(x))

  def dump(f: ParseError) {
    println(f.position)
    println(f.formatExpectedAsString)
    println(f.formatTraces)
  }

  def snippet(input: String, parsed: String): String = {
    val offset = parsed.length
    val aug = Predef.augmentString(input)
    aug.slice(offset - 50, offset) + s"[$offset]".red.to_s + aug.slice(offset, offset + 50)
  }

  def checkPath(root: Path, f: Path): Result = {
    val input    = try f.slurp() catch { case _: NoSuchFileException => return Skip }
    val fs       = f.toString
    val segments = (fs splitChar '/').toSet
    val path_s = fs stripPrefix root.toString stripPrefix "/" match {
      case s if s.length <= maxFileLen => s
      case s                           => "..." + (s takeRight maxFileLen - 3)
    }
    val isNeg  = segments("neg")
    val isSkip = (
         (input startsWith "#!")
      || (input.lines exists (_ matches unicodeRegex))
      || segments("failing")
    )
    lazy val parser = new ScalaSyntax(input)

    print(s"[%6s] $maxFileFmt  ".format(input.length, path_s))

    def finish(res: Result, str: String): Result = {
      println(res.ansi)
      if (str != "") println("\n" + str + "\n")
      res
    }
    def failMessage(error: ParseError): String = {
      import error._, position._
      def pos_s = "%s:%s:%s".format(f, line, column)
      s"""|Expected: $formatExpectedAsString
          |  at $pos_s
          |
          |${parser formatErrorLine error}
          |""".stripMargin.trim
    }

    if (isSkip) finish(Skip, "")
    else parser.CompilationUnit.run() match {
      case Success(`input`) if !isNeg => finish(Pass, "")
      case Failure(_) if isNeg        => finish(Pass, "")
      case Failure(t: ParseError)     => finish(Fail, failMessage(t))
      case Failure(t)                 => throw t
      case Success(s)                 => sys.error(s"Incomplete: $s")
    }
  }

  def main(args0: Array[String]): Unit = {
    val args    = ( if (args0.isEmpty) Seq(".") else args0.toSeq ) map (x => path(x))
    val results = args flatMap (root => scalaPaths(root) map (p => p -> checkPath(root, p)))
    val pass    = results filter (_._2 == Pass)
    val skip    = results filter (_._2 == Skip)
    val fail    = results filter (_._2 == Fail)
    val total   = skip.length + pass.length + fail.length

    println(s"%s tests: %s pass, %s fail, %s skipped".format(total, pass.length, fail.length, skip.length))

    if (fail.nonEmpty) {
      println("\nSkipped:\n")
      skip map (_._1) foreach println
      println("\nFailures:\n")
      val paths = fail map (_._1)
      paths foreach println
      runtimeException("There were (%s) test failures.".format(paths.length))
    }
  }
}
