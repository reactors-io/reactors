package io.reactors






abstract class DataBuffer {
  def readChunk: Data

  def writeChunk: Data
}
