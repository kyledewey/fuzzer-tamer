package tamer

import scala.annotation.tailrec

import java.io._
import java.nio.file.{DirectoryStream, Files, FileSystems, Path}
import java.util.concurrent._

// -dir: Directory to iterative over
// -extension: extension for files of interest
//
// A thread-safe iterator over a directory
class DirectoryIterator(dir: String, extension: String) {
  private val stream = 
    Files.newDirectoryStream(
      FileSystems.getDefault.getPath(dir),
      "*" + extension)
  private val iterator = stream.iterator

  def nextFile(): Option[File] = {
    try {
      Some(iterator.next.toFile)
    } catch {
      case _: NoSuchElementException => {
	stream.close()
	None
      }
    }
  }
}

// -parser: Does the parsing of bug-exhibiting files
// -sourceDir: directory containing bug-exhibiting files
// -extension: extension for bug-exhibiting files
// -resultDir: directory to put bug equivalence class information
//
// It would be nice to do this with one big fold, but we cannot
// do this for performance reasons.  Instead, we can look at input
// files in parallel.
class Tamer[T <: ParseResult[T]](
  val parser: Parser[T],
  val sourceDir: String,
  val extension: String,
  val resultDir: String) {
  
  private val directoryIterator =
    new DirectoryIterator(sourceDir, extension)
  
  class TamerRunnable extends Runnable {
    def run() {
      @tailrec
      def runWithFile(opFile: Option[File]) {
	opFile match {
	  case Some(file) => {
	    putInResult(parser.parseFile(file))
	    runWithFile(directoryIterator.nextFile)
	  }
	  case None => ()
	}
      }
      runWithFile(directoryIterator.nextFile)
    }
  }

  class EquivalenceClass(private val representative: T,
			 private val id: Int) {
    // begin constructor
    private val output =
      new BufferedWriter(
	new OutputStreamWriter(
	  new FileOutputStream(
	    new File(resultDir + "/" + "bug" + id.toString))))
    writeResult(representative)
    // end constructor

    private def sameClassAs(other: T): Boolean = {
      representative == other || representative.sameClassAs(other)
    }

    private def writeResult(result: T) {
      output.synchronized {
	output.write(result.sourceFile.getPath + "\n")
      }
    }

    // returns true if we handled it, meaning it was written
    // out, else false.
    def handleResult(result: T): Boolean = {
      if (sameClassAs(result)) {
	writeResult(result)
	true
      } else {
	false
      }
    }

    // indicates we are done with this equivalence class
    def done() {
      output.close()
    }
  }

  private var equivClasses: List[EquivalenceClass] = List()
  private var equivClassCurrentId = 0
  private val equivClassesMonitor = new Object()

  // returns true if the result was handled, else false
  private def handleResult(result: T): Boolean = {
    @tailrec
    def handler(equiv: List[EquivalenceClass]): Boolean = {
      equiv match {
	case head :: tail => {
	  if (head.handleResult(result)) {
	    true
	  } else {
	    handler(tail)
	  }
	}
	case Nil => false
      }
    }
    handler(equivClasses)
  }

  // only to be called when we did not find an approriate equivalence
  // class for some input
  private def addEquivClassFor(result: T) {
    equivClassesMonitor.synchronized {
      // possible that another was added in between this point
      if (!handleResult(result)) {
	equivClasses ::= new EquivalenceClass(result, equivClassCurrentId)
	equivClassCurrentId += 1
      }
    }
  }

  private def putInResult(result: T) {
    if (!handleResult(result)) {
      addEquivClassFor(result)
    }
  }

  private def done() {
    equivClasses.foreach(_.done())
  }

  def run() {
    val numThreads = Runtime.getRuntime().availableProcessors()
    val pool = Executors.newFixedThreadPool(numThreads)
    (0 until numThreads).foreach(_ =>
      pool.submit(new TamerRunnable()))
    pool.shutdown()
    pool.awaitTermination(java.lang.Long.MAX_VALUE, TimeUnit.SECONDS)
    done()
  }
}

