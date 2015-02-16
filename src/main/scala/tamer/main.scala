package tamer

case class MalformedArgsException(msg: String) extends Exception(msg)

case class Args[T <: ParseResult[T]](
  parser: Parser[T],
  sourceDir: String,
  extension: String,
  resultDir: String) {

  def makeTamer(): Tamer[T] = {
    new Tamer(parser, sourceDir, extension, resultDir)
  }
}

object Args {
  def fileExists(fileName: String): Boolean = {
    import java.io.File
    new File(fileName).exists
  }

  // throws a MalformedArgsException if the file doesn't exist
  def ensureFileExists(fileName: String) {
    if (!fileExists(fileName)) {
      throw new MalformedArgsException(
	"No such file with name: " + fileName)
    }
  }

  // throws a MalformedArgsException if they were in some way
  // malformed
  def parseArgs[T <: ParseResult[T]](args: List[String]): Args[T] = {
    args match {
      case parserName :: sourceDir :: extension :: resultDir :: Nil => {
	val parser = ParserIndex.getParser(parserName).getOrElse(
	  throw MalformedArgsException(
	    "Unknown parser name: " + parserName)).asInstanceOf[Parser[T]]
	ensureFileExists(sourceDir)
	ensureFileExists(resultDir)
	Args(parser, sourceDir, extension, resultDir)
      }
      case _ =>
	throw MalformedArgsException(
	  "Needs four parameters")
    }
  }
} // Args

object Main {
  // Takes the following command-line arguments:
  // -Type of parser to use for reading output file
  // -Directory containing bug-exhibiting files
  // -Extension for bug-exhibiting files
  // -Directory to put bug equivalence class information
  //
  // The bug equivalence class information is a series of files,
  // one per bug.  Each file contains a list of original input
  // files which exhibit the same bug.
  def main(args: Array[String]) {
    try {
      Args.parseArgs(args.toList).makeTamer.run()
    } catch {
      case MalformedArgsException(msg) => {
	println("Malformed arguments: " + msg)
      }
    }
  } // main
}
