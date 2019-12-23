package korolev.internal

import korolev.internal.Frontend.ModifyDomProcedure
import levsha.Id
import levsha.impl.DiffRenderContext.ChangesPerformer

import scala.collection.mutable

private[korolev] class RemoteDomChangesPerformer extends ChangesPerformer {

  val buffer: mutable.ArrayBuffer[Any] =
    mutable.ArrayBuffer.empty[Any]

  def remove(id: Id): Unit =
    buffer.append(ModifyDomProcedure.Remove.code, id.parent.get.mkString, id.mkString)

  def createText(id: Id, text: String): Unit =
    buffer.append(ModifyDomProcedure.CreateText.code, id.parent.get.mkString, id.mkString, text)

  def create(id: Id, xmlNs: String, tag: String): Unit = {
    val parent = id.parent.fold("0")(_.mkString)
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    buffer.append(ModifyDomProcedure.Create.code, parent, id.mkString, pXmlns, tag)
  }

  def removeStyle(id: Id, name: String): Unit = {
    buffer.append(ModifyDomProcedure.RemoveStyle.code, id.mkString, name)
  }

  def setStyle(id: Id, name: String, value: String): Unit = {
    buffer.append(ModifyDomProcedure.SetStyle.code, id.mkString, name, value)
  }

  def setAttr(id: Id, xmlNs: String, name: String, value: String): Unit = {
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    buffer.append(ModifyDomProcedure.SetAttr.code, id.mkString, pXmlns, name, value, false)
  }

  def removeAttr(id: Id, xmlNs: String, name: String): Unit = {
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    buffer.append(ModifyDomProcedure.RemoveAttr.code, id.mkString, pXmlns, name, false)
  }

}
