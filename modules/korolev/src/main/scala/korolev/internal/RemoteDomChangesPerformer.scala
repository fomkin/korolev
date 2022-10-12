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
import levsha.FastId
import levsha.impl.DiffRenderContext.ChangesPerformer

import scala.collection.mutable

private[korolev] class RemoteDomChangesPerformer extends ChangesPerformer {

  final val buffer = new mutable.StringBuilder()

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

  private def appendStringEscape(s: String): Unit = {
    buffer.append('"')
    jsonEscape(buffer, s, unicode = true)
    buffer.append('"')
    buffer.append(',')
  }

  private def appendXmlNs(xmlNs: String): Unit = {
    if (xmlNs eq levsha.XmlNs.html.uri) {
      buffer.append('0')
      buffer.append(',')
    } else appendString(xmlNs)
  }

  def remove(id: FastId): Unit = {
    append(ModifyDomProcedure.Remove.codeString)
    appendParentId(id)
    appendId(id)
  }

  def createText(id: FastId, text: String): Unit = {
    append(ModifyDomProcedure.CreateText.codeString)
    appendParentId(id)
    appendId(id)
    appendStringEscape(text)
  }

  def create(id: FastId, xmlNs: String, tag: String): Unit = {
    append(ModifyDomProcedure.Create.codeString)
    if (!id.hasParent) {
      appendString("0")
    } else {
      appendParentId(id)
    }
    appendId(id)
    appendXmlNs(xmlNs)
    appendString(tag)
  }

  def removeStyle(id: FastId, name: String): Unit = {
    append(ModifyDomProcedure.RemoveStyle.codeString)
    appendId(id)
    appendString(name)
  }

  def setStyle(id: FastId, name: String, value: String): Unit = {
    append(ModifyDomProcedure.SetStyle.codeString)
    appendId(id)
    appendString(name)
    appendStringEscape(value)
  }

  def setAttr(id: FastId, xmlNs: String, name: String, value: String): Unit = {
    append(ModifyDomProcedure.SetAttr.codeString)
    appendId(id)
    appendXmlNs(xmlNs)
    appendString(name)
    appendStringEscape(value)
    append("false")
  }

  def removeAttr(id: FastId, xmlNs: String, name: String): Unit = {
    append(ModifyDomProcedure.RemoveAttr.codeString)
    appendId(id)
    appendXmlNs(xmlNs)
    appendString(name)
    append("false")
  }

}
