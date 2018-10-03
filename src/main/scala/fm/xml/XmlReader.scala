/*
 * Copyright 2014 Frugal Mechanic (http://frugalmechanic.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fm.xml

import com.ctc.wstx.stax.WstxInputFactory
import fm.common.Implicits._
import fm.common.{Logging, Resource}
import fm.lazyseq.ResourceLazySeq
import fm.xml.RichXMLStreamReader2.toRichXMLStreamReader2
import java.io.{InputStream, Reader, StringReader}
import javax.xml.stream.XMLInputFactory
import org.codehaus.stax2.XMLStreamReader2
import org.codehaus.stax2.util.StreamReader2Delegate
import scala.reflect.ClassTag
import scala.util.Try

object XmlReader {
  // Backward source-compatible support for specifying a simple itemPath/element type
  def apply[T: ClassTag](rootName: String, itemPath: String, defaultNamespaceURI: String, overrideDefaultNamespaceURI: String, resource: Resource[Reader]): XmlReader[T] = {
    val path: XmlReaderPath[T, T] = XmlReaderPath[T, T](itemPath, identity)

    new XmlReader[T](
      rootName,
      defaultNamespaceURI,
      overrideDefaultNamespaceURI,
      resource,
      IndexedSeq(path)
    )
  }

  def apply[T](rootName: String, resource: Resource[Reader], targetHead: XmlReaderPath[_,T], targetsRest: XmlReaderPath[_,T]*): XmlReader[T] = {
    apply(rootName, resource, (targetHead +: targetsRest).toIndexedSeq)
  }

  def apply[T](rootName: String, resource: Resource[Reader], targets: IndexedSeq[XmlReaderPath[_,T]]): XmlReader[T] = {
    XmlReader(
      rootName = rootName,
      defaultNamespaceURI = "",
      overrideDefaultNamespaceURI = "",
      resource = resource,
      targets = targets
    )
  }

  private val inputFactory: WstxInputFactory = new WstxInputFactory()
  inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
  inputFactory.configureForSpeed()

  def withXMLStreamReader2[T](s: String)(f: XMLStreamReader2 => T): T = {
    import Resource._
    Resource.using(inputFactory.createXMLStreamReader(new StringReader(s)).asInstanceOf[XMLStreamReader2])(f)
  }

  def withXMLStreamReader2[T](is: InputStream)(f: XMLStreamReader2 => T): T = {
    import Resource._
    Resource.using(inputFactory.createXMLStreamReader(is).asInstanceOf[XMLStreamReader2])(f)
  }

  /**
    * Attempt to get the root element name from the passed in XML.
    *
    * Note: This is really more of a "getFirstElementName" since it will just return the first element it finds
    *       even if if it not a valid XML document.  e.g. "<foo></bar>" will return "foo"
    *
    * @param xml The XML
    * @return The root element name or None if there is none
    */
  def getRootElementName(xml: String): Option[String] = {
    if (xml.isNullOrBlank) return None

    withXMLStreamReader2(xml){ reader: XMLStreamReader2 =>
      Try {
        reader.seekToRootElement()
        reader.getLocalName
      }.toOption
    }
  }

  /**
    * Overrides the defaultNamespaceURI on elements/attributes.  This is used for the PIES feeds when the default namespace is not set
    * in the feed but JAXB expects the default namespace to be "http://www.aftermarket.org"
    */
  private class DefaultNamespaceStreamReaderDelegate(self: XMLStreamReader2, defaultNamespaceURI: String) extends StreamReader2Delegate(self) {
    Predef.require(defaultNamespaceURI.isNotNullOrBlank, "Expected defaultNamespace to be not blank")
    
    override def getNamespaceURI(): String = {
      val ns: String = super.getNamespaceURI()
      if (null == ns) defaultNamespaceURI else ns
    }

  }

  /**
    * Similar to DefaultNamespaceStreamReaderDelegate but just completely overrides the namespace.  Used when people set the namespace
    * to something like "www.aftermarket.org" when it should be "http://www.aftermarket.org"
    */
  private class OverrideDefaultNamespaceStreamReaderDelegate(self: XMLStreamReader2, overrideDefaultNamespaceURI: String) extends StreamReader2Delegate(self) {
    Predef.require(overrideDefaultNamespaceURI.isNotNullOrBlank, "Expected overrideDefaultNamespaceURI to be not blank")
    
    override def getNamespaceURI(): String = {
      // If the prefix is blank (which I thinks means we are using the default namespace) use the override
      if (getPrefix().isNullOrBlank) overrideDefaultNamespaceURI else super.getNamespaceURI()
    }
  }


  private case class ReaderStack(maxSize: Int) extends IndexedSeq[String] {
    private var depth: Int = 0
    private val array = new Array[String](maxSize)
    final def length: Int = depth

    def pushElement(elementName: String): Unit = {
      depth += 1
      array(depth-1) = elementName
    }

    def pop(): Unit = {
      if (depth > 0) {
        array(depth-1) = null
        depth -= 1
      }
    }

    def apply(idx: Int): String = array(idx)
  }
}

/**
  * defaultNamespaceURI - If no namespace is set, use this as a default
  * overrideDefaultNamespaceURI - Override the namespace and just use this instead
  */
final case class XmlReader[T](
  rootName: String,
  defaultNamespaceURI: String,
  overrideDefaultNamespaceURI: String,
  protected val resource: Resource[Reader],
  targets: IndexedSeq[XmlReaderPath[_, T]]
) extends ResourceLazySeq[T, Reader] with Logging {
  import XmlReaderPath._

  if (defaultNamespaceURI.isNotNullOrBlank) require(overrideDefaultNamespaceURI.isNullOrBlank, "Can't set both defaultNamespaceURI and overrideDefaultNamespaceURI")
  if (overrideDefaultNamespaceURI.isNotNullOrBlank) require(defaultNamespaceURI.isNullOrBlank, "Can't set both defaultNamespaceURI and overrideDefaultNamespaceURI")

  private def wrapXMLStreamReader2(r: XMLStreamReader2): XMLStreamReader2 = {
    if (overrideDefaultNamespaceURI.isNotNullOrBlank) new XmlReader.OverrideDefaultNamespaceStreamReaderDelegate(r, overrideDefaultNamespaceURI)
    else if (defaultNamespaceURI.isNotNullOrBlank) new XmlReader.DefaultNamespaceStreamReaderDelegate(r, defaultNamespaceURI)
    else r
  }

  protected def foreachWithResource[U](f: T => U, reader: Reader): Unit = {
    val inputFactory = new WstxInputFactory()
    inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    inputFactory.configureForSpeed()

    import Resource.toCloseable
    import XmlReader.ReaderStack

    Resource.using(wrapXMLStreamReader2(inputFactory.createXMLStreamReader(XmlInvalidCharFilter(reader)).asInstanceOf[XMLStreamReader2])) { xmlStreamReader: XMLStreamReader2 =>
      var done: Boolean = false

      val maxPathDepth: Int = targets.map{ _.targetDepth }.max
      val currentPath: ReaderStack = ReaderStack(maxPathDepth)

      @inline def checkAndPopCurrentPath(): Unit = {
        // If the path goes negative, we need to gracefully close reading
        if (currentPath.length > 0) currentPath.pop()
        else done = true
      }

      // Move to the ROOT element (skipping stuff like DTDs) and check it's name and then move to the next parsing event
      xmlStreamReader.seekFirstEventPastRootElement(rootName)

      while (!done) {
        var foundMatchingElement: Boolean = false

        // Handle Start Element
        if (xmlStreamReader.isStartElement) {
          currentPath.pushElement(xmlStreamReader.getLocalName)

          // Optimized to avoid a closure
          var i: Int = 0
          var currentPathHasCandidatePaths: Boolean = false
          foundMatchingElement = false

          while (!foundMatchingElement && i < targets.length) {
            val candidatePath: XmlReaderPath[_, T] = targets(i)

            val matchType: XmlReaderPathMatchType = candidatePath.getMatchType(currentPath)

            if (matchType.isPrefixMatch || matchType.isElementMatch) currentPathHasCandidatePaths = true

            if (matchType.isElementMatch) {
              f(candidatePath.readValue(xmlStreamReader))

              // If we consumed this element, then
              //   1) it already consumed the end element, so don't trigger the xmlStreamReader.next
              //   2) we need to pop it from the stack
              foundMatchingElement = true
              checkAndPopCurrentPath()
            }

            i += 1
          }

          // Did not find any possible matches, so ignore this path
          if (!currentPathHasCandidatePaths) {
            // This isn't an element we care about, skip it (including anything under it)
            xmlStreamReader.skipElement()
            checkAndPopCurrentPath()
          }
        // Handle End Element
        } else if (xmlStreamReader.isEndElement) {
          checkAndPopCurrentPath()
        }

        // Continue reading the stream

        // If we found the matching element, the end element has already been consumed and the stream should not be .next()ed
        if (foundMatchingElement) {
          foundMatchingElement = false
        // Otherwise step to the next element in the stream
        } else if (!done && xmlStreamReader.hasNext()) {
          xmlStreamReader.next()
        } else if (!xmlStreamReader.hasNext) {
          done = true
        }
      }

      consumeRestOfStream(xmlStreamReader)
    }
  }

  protected def consumeRestOfStream(xmlStreamReader: XMLStreamReader2): Unit = {
    var done: Boolean = false

    while (!done && xmlStreamReader.hasNext()) {
      try {
        xmlStreamReader.next()
        if(xmlStreamReader.isStartElement()) logger.warn("Unexpected start element: "+xmlStreamReader.getLocalName())
        if(xmlStreamReader.isEndElement()) logger.warn("Unexpected end element: "+xmlStreamReader.getLocalName())
      } catch {
        case ex: Exception =>
          logger.error("Caught Exception reader XML after we've already seen the closing tag: "+ex.getMessage)
          done = true
      }
    }
  }
}

