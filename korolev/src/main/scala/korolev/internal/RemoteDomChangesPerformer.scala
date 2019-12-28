package korolev.internal

import korolev.internal.Frontend.ModifyDomProcedure
import levsha.Id
import levsha.impl.DiffRenderContext.ChangesPerformer

import scala.collection.mutable

private[korolev] class RemoteDomChangesPerformer extends ChangesPerformer {

  val buffer: mutable.ArrayBuffer[Any] =
    mutable.ArrayBuffer.empty[Any]

  def remove(id: Id): Unit = {
    buffer += ModifyDomProcedure.Remove.code
    buffer += id.parent.get.mkString
    buffer += id.mkString
  }

  def createText(id: Id, text: String): Unit = {
    buffer += ModifyDomProcedure.CreateText.code
    buffer += id.parent.get.mkString
    buffer += id.mkString
  }

  def create(id: Id, xmlNs: String, tag: String): Unit = {
    val parent = id.parent.fold("0")(_.mkString)
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    buffer += ModifyDomProcedure.Create.code
    buffer += parent
    buffer += id.mkString
    buffer += pXmlns
    buffer += tag
  }

  def removeStyle(id: Id, name: String): Unit = {
    buffer += ModifyDomProcedure.RemoveStyle.code
    buffer += id.mkString
    buffer += name
  }

  def setStyle(id: Id, name: String, value: String): Unit = {
    buffer += ModifyDomProcedure.SetStyle.code
    buffer += id.mkString
    buffer += name
    buffer += value
  }

  def setAttr(id: Id, xmlNs: String, name: String, value: String): Unit = {
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    buffer += ModifyDomProcedure.SetAttr.code
    buffer += id.mkString
    buffer += pXmlns
    buffer += name
    buffer += value
    buffer += false
  }

  def removeAttr(id: Id, xmlNs: String, name: String): Unit = {
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    buffer += ModifyDomProcedure.RemoveAttr.code
    buffer += id.mkString
    buffer += pXmlns
    buffer += name
    buffer += false
  }

}
