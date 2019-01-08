/*
 * Copyright 2018 Frugal Mechanic (http://frugalmechanic.com)
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

import fm.common.Implicits._
import fm.common.Logging
import javax.xml.bind.{JAXBContext, Unmarshaller}
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import org.codehaus.stax2.XMLStreamReader2
import scala.reflect.{ClassTag, classTag}

object XmlReaderPath {
  // Apply helper for reading a non-mapped value
  def apply[XmlValue: ClassTag](itemPath: String): XmlReaderPath[XmlValue, XmlValue] = XmlReaderPath[XmlValue, XmlValue](itemPath, identity)

  sealed trait XmlReaderPathMatchType {
    def isPrefixMatch: Boolean = this === XmlReaderPathMatchType.PrefixMatch
    def isFullMatch: Boolean = this === XmlReaderPathMatchType.FullMatch
    def isNoMatch: Boolean = this === XmlReaderPathMatchType.NoMatch
  }
  object XmlReaderPathMatchType {
    case object FullMatch extends XmlReaderPathMatchType
    case object PrefixMatch extends XmlReaderPathMatchType
    case object NoMatch extends XmlReaderPathMatchType
  }
}



/**
  *
  * @param itemPath The XPath-like path to the element we are interested in.  (e.g. "part", "items/part", etc)
  * @param toMappedValue Map the XmlValue into another return type, this should match the return type in MultiXmlReader
  * @tparam XmlValue The unmarshalled element class type
  * @tparam MappedXmlValue A mapped XmlValue type
  */
final case class XmlReaderPath[XmlValue: ClassTag, MappedXmlValue](
  itemPath: String,
  toMappedValue: XmlValue => MappedXmlValue
) extends Logging {
  import XmlReaderPath._

  // Make this public, so we can use it for the XmlWriter JAXBContext
  val itemClass: Class[XmlValue] = classTag[XmlValue].runtimeClass.asInstanceOf[Class[XmlValue]]
  private[this] val jaxbContext: JAXBContext = JAXBContext.newInstance(itemClass)
  private[this] val unmarshaller: Unmarshaller = jaxbContext.createUnmarshaller()

  // The XPath-like path to the element we are interested in
  // part => Array("part"), items/part => Array("items","part")
  private[xml] val path: Array[String] = itemPath.split('/')

  // The name of the element we care about (last part of the path)
  // items/part => part
  private[xml] val itemName: String = path.last

  private[xml] val targetDepth: Int = path.length

  private[xml] def readValue(xmlStreamReader: XMLStreamReader2): MappedXmlValue =  {
    xmlStreamReader.require(START_ELEMENT, null, itemName)

    val value: XmlValue = unmarshaller.unmarshal(xmlStreamReader, itemClass).getValue
    toMappedValue(value)
  }

  /**
    * Given a current element nesting sequence, getMatchType returns whether this path is an exact match, no match, or if the prefix matches.
    *
    * Example: if the itemPath is "items/part" then:
    *
    *   When currentPath is IndexedSeq("items") the match type will be XmlReaderPathMatchType.PrefixMatch
    *   When currentPath is IndexedSeq("items", "part") the match type will be XmlReaderPathMatchType.FullMatch
    *   When currentPath is IndexedSeq("items", "price") the match type will be XmlReaderPathMatchType.NoMatch
    *   When currentPath is IndexedSeq("prices") the match type will be XmlReaderPathMatchType.NoMatch
    *
    * @param currentPath An IndexedSeq of the current nested XML-element tree.  (e.g. "items/part/price" => Seq("items", "part", "price")
    * @return
    */
  private[xml] def getMatchType(currentPath: IndexedSeq[String]): XmlReaderPathMatchType = {
    if (currentPath.isEmpty || currentPath.length > targetDepth) return XmlReaderPathMatchType.NoMatch

    // Optimized to avoid a closure
    var idx: Int = 0

    while (idx < currentPath.length && currentPath(idx) === path(idx)) {
      idx += 1
    }

    if (idx === targetDepth) XmlReaderPathMatchType.FullMatch
    else if (idx === currentPath.length) XmlReaderPathMatchType.PrefixMatch
    else XmlReaderPathMatchType.NoMatch
  }

  override def toString(): String = {
    s"XmlReaderPath[${itemClass.toString}](itemPath: ${itemPath})"
  }
}