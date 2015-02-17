package tamer

import scala.annotation.tailrec

import java.io.File

case class RustStackTraceEntry(path: Seq[String])
case class RustAssertionViolation(
  text: String,
  filename: String,
  line: Int)

sealed abstract class RustParseResult(sourceFile: File) extends ParseResult[RustParseResult](sourceFile) {
  def sameClassAs(other: RustParseResult): Boolean = {
    (this, other) match {
      case (r1: RustStackTrace, r2: RustStackTrace) =>
	r1.sameClassAsStackTrace(r2)
      case (_: RustStackOverflow, _: RustStackOverflow) | (_: RustUnknown, _: RustUnknown) => true
      case _ => false
    }
  }
}
case class RustUnknown(file: File) extends RustParseResult(file)
case class RustStackOverflow(file: File) extends RustParseResult(file)
case class RustStackTrace(
  file: File,
  violation: RustAssertionViolation,
  entries: List[RustStackTraceEntry]) extends RustParseResult(file) {

  lazy val entriesSnippet =
    entries.take(RustStackTrace.NUM_TO_TAKE)

  def sameClassAsStackTrace(other: RustStackTrace): Boolean = {
    (violation == other.violation &&
     entriesSnippet == other.entriesSnippet)
  }
}

object RustStackTrace {
  val NUM_TO_TAKE = 10

  private val AssertionViolationString =
    """thread .* panicked at '(.*)', .*?([^/]+\.rs):(\d+)""".r
  private val BacktraceEntryString = 
    """\s*\d+:\s*0x.* \- (.*)""".r

  def parseAssertionViolation(line: String): Option[RustAssertionViolation] = {
    line match {
      case AssertionViolationString(text, filename, line) =>
	Some(RustAssertionViolation(text, filename, line.toInt))
      case _ => None
    }
  }

  def parseStackTraceEntry(line: String): Option[RustStackTraceEntry] = {
    line match {
      case BacktraceEntryString(pathString) =>
	Some(RustStackTraceEntry(pathString.split("::").toSeq))
      case _ => None
    }
  }

  def combineSomes[T](options: List[Option[T]]): List[T] = {
    options.filter(_.isDefined).map(_.get)
  }

  def parseStackTraceEntries(lines: List[String]): List[RustStackTraceEntry] = {
    combineSomes(lines.map(parseStackTraceEntry))
  }

  // assumes it starts on the first line
  def parseStackTrace(file: File, lines: List[String]): Option[RustStackTrace] = {
    for {
      _ <- if (lines.nonEmpty) Some(()) else None
      assertion <- parseAssertionViolation(lines.head)
    } yield {
      val parsedEntries = lines match {
	case _ :: "" :: "stack backtrace:" :: entries => {
	  parseStackTraceEntries(entries)
	}
	case _ => List()
      }
      RustStackTrace(file, assertion, parsedEntries)
    }
  }
} // RustStackTrace

object RustParser extends Parser[RustParseResult] {
  val PANIC_HEADER = Seq("error: internal compiler error: unexpected panic",
			 "note: the compiler unexpectedly panicked. this is a bug.",
			 "note: we would appreciate a bug report: http://doc.rust-lang.org/complement-bugreport.html",
			 "note: run with `RUST_BACKTRACE=1` for a backtrace")
  val STACK_OVERFLOW_HEADER = Seq("thread 'rustc' has overflowed its stack")

  val headerLines = Seq(PANIC_HEADER, STACK_OVERFLOW_HEADER)

  // returns which header matched, along with the lines after the header line
  @tailrec
  final def parseLinesUntilHeader(lines: List[String]): Option[(Seq[String], List[String])] = {
    def startsWithHeader(header: Seq[String]): Boolean = {
      val zipped = lines.zip(header)
      zipped.forall(
	{ case (s1, s2) => s1 == s2 }) && zipped.length >= header.length
    }

    headerLines.find(startsWithHeader) match {
      case Some(header) => Some((header, lines.drop(header.length)))
      case None => {
	lines match {
	  case _ :: tail => {
	    parseLinesUntilHeader(tail)
	  }
	  case _ => None
	}
      }
    }
  }

  def parseLines(file: File, lines: List[String]): RustParseResult = {
    lazy val unknown = RustUnknown(file)

    parseLinesUntilHeader(lines).map(
      { case (header, rest) => {
	  header match {
	    case PANIC_HEADER => 
	      RustStackTrace.parseStackTrace(file, rest).getOrElse(unknown)
	    case STACK_OVERFLOW_HEADER =>
	      RustStackOverflow(file)
	    case _ => sys.error("impossible") // shouldn't be possible
	  }
      } } ).getOrElse(unknown)
  }
}

