package tamer

import java.io.File

abstract class ParseResult[T <: ParseResult[T]](val sourceFile: File) {
  // determines if this parse result is in the same class as another
  def sameClassAs(other: T): Boolean
}

trait Parser[T <: ParseResult[T]] {
  def parseLines(file: File, lines: List[String]): T
  def parseFile(file: File): T = {
    parseLines(
      file,
      scala.io.Source.fromFile(file).getLines.toList)
  }
}

object ParserIndex {
  val index: Map[String, Parser[_]] = 
    Map("rust" -> RustParser)

  def getParser[T <: ParseResult[T]](name: String): Option[Parser[T]] = {
    index.get(name).map(_.asInstanceOf[Parser[T]])
  }
}
