package fm.xml

private final class XmlReaderPathStack(maxSize: Int) extends IndexedSeq[String] {
  private[this] var depth: Int = 0
  private[this] val array = new Array[String](maxSize)

  def length: Int = depth

  def pushElement(elementName: String): Unit = {
    if (depth < maxSize) array(depth) = elementName
    depth += 1
  }

  def pop(): Unit = {
    if (depth > 0) {
      depth -= 1
      if (depth < maxSize) array(depth) = null
    }
  }

  def apply(idx: Int): String = {
    if (idx >= depth) throw new ArrayIndexOutOfBoundsException("Depth is "+depth+" but requested index is "+idx)
    else if (idx >= maxSize) null // Arbitrarily chosen
    else array(idx)
  }
}