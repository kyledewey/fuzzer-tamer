package tamer

import java.io._

abstract class ParseResult[T <: ParseResult[T]](val sourceFile: File) {
  // determines if this parse result is in the same class as another
  def sameClassAs(other: T): Boolean
}

object Parser {
  // returns a sequence of strings contained in the file, without
  // newlines
  def fileToStrings(file: File): List[String] = {
    var retval: List[String] = List()
    val reader =
      new BufferedReader(
	new FileReader(file))
    var line = reader.readLine()
    while (line ne null) {
      retval ::= line
      line = reader.readLine()
    }
    reader.close()
    retval.reverse
  }
}

trait Parser[T <: ParseResult[T]] {
  def parseLines(file: File, lines: List[String]): T
  def parseFile(file: File): T = {
    parseLines(
      file,
      Parser.fileToStrings(file))
  }
}

object ParserIndex {
  val index: Map[String, Parser[_]] = 
    Map("rust" -> RustParser)

  def getParser[T <: ParseResult[T]](name: String): Option[Parser[T]] = {
    index.get(name).map(_.asInstanceOf[Parser[T]])
  }
}
