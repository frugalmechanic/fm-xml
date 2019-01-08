package fm.xml

import org.scalatest.{FunSuite, Matchers}

final class TestXmlReaderPathStack extends FunSuite with Matchers {
  test("Single Element Stack") {
    val stack: XmlReaderPathStack = new XmlReaderPathStack(1)

    stack.length should equal (0)
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(0)

    stack.pushElement("foo")
    stack.length should equal (1)
    stack(0) should equal ("foo")
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(1)

    stack.pushElement("bar")
    stack.length should equal (2)
    stack(0) should equal ("foo")
    stack(1) should equal (null)
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(2)

    stack.pushElement("baz")
    stack.length should equal (3)
    stack(0) should equal ("foo")
    stack(1) should equal (null)
    stack(2) should equal (null)
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(3)

    stack.pop()
    stack.length should equal (2)
    stack(0) should equal ("foo")
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(2)

    stack.pop()
    stack.length should equal (1)
    stack(0) should equal ("foo")
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(1)

    stack.pop()
    stack.length should equal (0)
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(0)

    stack.pop()
    stack.length should equal (0)
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(0)
  }

  test("Five Element Stack") {
    val stack: XmlReaderPathStack = new XmlReaderPathStack(5)

    stack.pushElement("one")
    stack.pushElement("two")
    stack.pushElement("three")
    stack.pushElement("four")
    stack.pushElement("five")
    stack.pushElement("six")

    stack.length should equal (6)

    stack(0) should equal ("one")
    stack(1) should equal ("two")
    stack(2) should equal ("three")
    stack(3) should equal ("four")
    stack(4) should equal ("five")
    stack(5) should equal (null)
    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(6)

    stack.pop()
    stack.length should equal (5)

    stack.pop()
    stack.length should equal (4)

    stack.pop()
    stack.length should equal (3)

    stack.pop()
    stack.length should equal (2)

    stack.pop()
    stack.length should equal (1)

    stack.pop()
    stack.length should equal (0)

    an [ArrayIndexOutOfBoundsException] should be thrownBy stack(0)
  }
}
