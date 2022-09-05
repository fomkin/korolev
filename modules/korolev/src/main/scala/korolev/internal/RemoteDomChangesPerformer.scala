/*
 * Copyright 2017-2020 Aleksey Fomkin
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

package korolev.internal

import korolev.internal.Frontend.ModifyDomProcedure
import levsha.{FastId, XmlNs}
import levsha.impl.DiffRenderContext.{ChangesPerformer, FastChangesPerformer}
import levsha.impl.internal.StringHelper.appendFromSource

import java.nio.ByteBuffer
import scala.collection.mutable

private[korolev] class RemoteDomChangesPerformer extends FastChangesPerformer {

  final val buffer = new mutable.StringBuilder()

  private def appendFromSourceEscape(source: ByteBuffer, offset: Int, bytesLength: Int): Unit = {
    if (bytesLength > 0) {
      var i = 0
      while (i < bytesLength) {
        val c = source.getChar(offset + i)
        jsonCharAppend(buffer, c, unicode = true)
        i += 2
      }
    }
  }

  private def append(s: String): Unit = {
    buffer.append(s)
    buffer.append(',')
  }

  private def appendId(id: FastId): Unit = {
    buffer.append('"')
    id.mkString(buffer)
    buffer.append('"')
    buffer.append(',')
  }

  private def appendParentId(id: FastId): Unit = {
    buffer.append('"')
    id.mkStringParent(buffer)
    buffer.append('"')
    buffer.append(',')
  }

  private def appendString(s: String): Unit = {
    buffer.append('"')
    buffer.append(s)
    buffer.append('"')
    buffer.append(',')
  }

  private def appendString(source: ByteBuffer, offset: Int, byteLength: Int): Unit = {
    buffer.append('"')
    appendFromSource(source, buffer, offset, byteLength)
    buffer.append('"')
    buffer.append(',')
  }

  private def appendStringEscape(source: ByteBuffer, offset: Int, byteLength: Int): Unit = {
    buffer.append('"')
    appendFromSourceEscape(source, offset, byteLength)
    buffer.append('"')
    buffer.append(',')
  }

  private def appendXmlNs(xmlNs: XmlNs): Unit = {
    buffer.append(xmlNs.code)
    buffer.append(',')
//    if (xmlNs eq levsha.XmlNs.HTML) {
//      buffer.append('0')
//      buffer.append(',')
//    } else appendString(xmlNs.uri)
  }

  def remove(id: FastId): Unit = {
    append(ModifyDomProcedure.Remove.codeString)
    appendParentId(id)
    appendId(id)
  }

  def createText(id: FastId, source: ByteBuffer, offset: Int, length: Int): Unit = {
    append(ModifyDomProcedure.CreateText.codeString)
    appendParentId(id)
    appendId(id)
    appendStringEscape(source, offset, length)
  }

  def create(id: FastId, xmlNs: XmlNs, source: ByteBuffer, offset: Int, bytesLength: Int): Unit = {
    append(ModifyDomProcedure.Create.codeString)
    if (!id.hasParent) {
      appendString("0")
    } else {
      appendParentId(id)
    }
    appendId(id)
    appendXmlNs(xmlNs)
    appendString(source, offset, bytesLength)
  }

  def removeStyle(id: FastId, source: ByteBuffer, offset: Int, bytesLength: Int): Unit = {
    append(ModifyDomProcedure.RemoveStyle.codeString)
    appendId(id)
    appendString(source, offset, bytesLength)
  }

  def setStyle(id: FastId, source: ByteBuffer, nameOffset: Int, nameBytesLength: Int, valueOffset: Int, valueBytesLength: Int): Unit = {
    append(ModifyDomProcedure.SetStyle.codeString)
    appendId(id)
    appendString(source, nameOffset, nameBytesLength)
    appendStringEscape(source, valueOffset, valueBytesLength)
  }

  def setAttr(id: FastId, xmlNs: XmlNs, source: ByteBuffer, nameOffset: Int, nameBytesLength: Int, valueOffset: Int, valueBytesLength: Int): Unit = {
    append(ModifyDomProcedure.SetAttr.codeString)
    appendId(id)
    appendXmlNs(xmlNs)
    appendString(source, nameOffset, nameBytesLength)
    appendStringEscape(source, valueOffset, valueBytesLength)
    append("false")
  }

  def removeAttr(id: FastId, xmlNs: XmlNs, source: ByteBuffer, offset: Int, bytesLength: Int): Unit = {
    append(ModifyDomProcedure.RemoveAttr.codeString)
    appendId(id)
    appendXmlNs(xmlNs)
    appendString(source, offset, bytesLength)
    append("false")
  }
}
