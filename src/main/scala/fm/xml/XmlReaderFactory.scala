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

import fm.common.{InputStreamResource, Resource, SingleUseResource}
import java.io.{File, InputStream, Reader, StringReader}
import scala.reflect.ClassTag

object XmlReaderFactory {
  // Single-item/class helpers
  def apply[T: ClassTag](
    rootName: String,
    itemName: String,
    defaultNamespaceURI: String = "",
    overrideDefaultNamespaceURI: String = "",
  ): XmlReaderFactory[T] = {
    new XmlReaderFactory(rootName, defaultNamespaceURI = defaultNamespaceURI, overrideDefaultNamespaceURI = overrideDefaultNamespaceURI, IndexedSeq(XmlReaderPath(itemName)))
  }

  //
  def apply[T: ClassTag](
    rootName: String,
    target: XmlReaderPath[_, T],
    rest: XmlReaderPath[_, T]*
  ): XmlReaderFactory[T] = {
    new XmlReaderFactory(rootName, defaultNamespaceURI = "", overrideDefaultNamespaceURI = "", (target +: rest).toIndexedSeq)
  }

  def apply[T: ClassTag](
    rootName: String,
    defaultNamespaceURI: String,
    overrideDefaultNamespaceURI: String,
    target: XmlReaderPath[_, T],
    rest: XmlReaderPath[_, T]*
  ): XmlReaderFactory[T] = {
    new XmlReaderFactory(rootName, defaultNamespaceURI, overrideDefaultNamespaceURI, (target +: rest).toIndexedSeq)
  }
}

final class XmlReaderFactory[T] private (rootName: String, defaultNamespaceURI: String, overrideDefaultNamespaceURI: String, targets: IndexedSeq[XmlReaderPath[_, T]]) {
  def reader(f: File)               : XmlReader[T] = reader(InputStreamResource.forFileOrResource(f))
  def reader(is: InputStream)       : XmlReader[T] = reader(InputStreamResource.forInputStream(is))
  def reader(r: InputStreamResource): XmlReader[T] = reader(r.reader())
  def reader(s: String)             : XmlReader[T] = reader(new StringReader(s))
  def reader(r: Reader)             : XmlReader[T] = reader(SingleUseResource(r))
  def reader(r: Resource[Reader])   : XmlReader[T] = XmlReader(rootName, defaultNamespaceURI, overrideDefaultNamespaceURI, r, targets)
}