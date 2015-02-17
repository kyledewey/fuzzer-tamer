package tamer

import java.io._

object AsyncFile {
  val WRITE_IF_BUFFER_EXCEEDS = 10000  
}

// We have the following constraints:
// 1.) Only one thread will ever manipulate one async file at a time
// 2.) We want to abstract over whether or not the file is open or closed.
//     For performance it's better if it's open, though this might not be possible
//     if we have a lot of open files.
// 3.) We don't care about the order in which items are written.
// 4.) It's ok if the calling thread blocks on occasion
class AsyncFile(private val file: File) {
  // begin instance variables
  private var buffer = List[String]()
  private var size = 0
  // end instance variables

  def append(what: String) {
    import java.io._
    buffer ::= what
    size += what.length
    if (size > AsyncFile.WRITE_IF_BUFFER_EXCEEDS) {
      flush()
    }
  }

  def flush() {
    val writer = new BufferedWriter(
      new OutputStreamWriter(
        new FileOutputStream(
          file)))
    // we could reverse it, but ordering is irrelevant
    writer.write(buffer.mkString("\n"))
    if (size > 0) {
      writer.newLine()
    }
    writer.close()
    buffer = List()
    size = 0
  }

  def close() {
    flush()
  }
}
