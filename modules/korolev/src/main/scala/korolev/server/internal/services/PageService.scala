package korolev.server.internal.services

import korolev.Context.Binding
import korolev.Qsid
import korolev.effect.Effect
import korolev.server.KorolevServiceConfig
import korolev.server.internal.Html5RenderContext
import korolev.server.internal.services.PageService.UpgradeHeadRenderContextProxy
import levsha.{Id, RenderContext, StatefulRenderContext, XmlNs}

final class PageService[F[_]: Effect, S, M](config: KorolevServiceConfig[F, S, M]) {

  type B = Binding[F, S, M]

  private val clw = {
    val rc = new Html5RenderContext[F, S, M]()
    config.connectionLostWidget(rc)
    rc.mkString
  }

  def appendScripts(rc: RenderContext[_], qsid: Qsid): Unit = {
    val rp = config.rootPath
    val heartbeatInterval = config.heartbeatInterval.toMillis
    val kfg = s"window['kfg']={sid:'${qsid.sessionId}',r:'$rp',clw:'$clw',heartbeatInterval:$heartbeatInterval}"

    rc.openNode(XmlNs.html, "script")
    rc.addTextNode(kfg)
    rc.closeNode("script")
    rc.openNode(XmlNs.html, "script")
    rc.setAttr(XmlNs.html, name = "src", (rp / "static/korolev-client.min.js").mkString)
    rc.setAttr(XmlNs.html, name = "defer", "")
    rc.closeNode("script")
  }


  def setupStatefulProxy(rc: StatefulRenderContext[B],
                         qsid: Qsid,
                         k: (StatefulRenderContext[B], B) => Unit): StatefulRenderContext[Binding[F, S, M]] = {
    new StatefulRenderContext[B] with UpgradeHeadRenderContextProxy[B]  { proxy =>
      val underlyingRenderContext: RenderContext[B] = rc
      def upgradeHead(): Unit = appendScripts(rc, qsid)
      def subsequentId: Id = rc.subsequentId
      def currentId: Id = rc.currentId
      def currentContainerId: Id = rc.currentContainerId
      override def addMisc(misc: B): Unit = k(proxy, misc)
    }
  }

  def setupStatelessProxy(rc: RenderContext[B],
                         qsid: Qsid): RenderContext[Binding[F, S, M]] = {
    new UpgradeHeadRenderContextProxy[B]  { proxy =>
      val underlyingRenderContext: RenderContext[B] = rc
      def upgradeHead(): Unit = appendScripts(rc, qsid)
    }
  }
}

object PageService {

  trait RenderContextProxy[-M] extends RenderContext[M] {
    def underlyingRenderContext: RenderContext[M]
    def openNode(xmlns: XmlNs, name: String): Unit = underlyingRenderContext.openNode(xmlns, name)
    def closeNode(name: String): Unit = underlyingRenderContext.closeNode(name)
    def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = underlyingRenderContext.setAttr(xmlNs, name, value)
    def setStyle(name: String, value: String): Unit = underlyingRenderContext.setStyle(name, value)
    def addTextNode(text: String): Unit = underlyingRenderContext.addTextNode(text)
    def addMisc(misc: M): Unit = underlyingRenderContext.addMisc(misc)
  }

  trait UpgradeHeadRenderContextProxy[-M] extends RenderContextProxy[M] {

    private var headWasOpened = false

    protected def upgradeHead(): Unit

    override def openNode(xmlNs: XmlNs, name: String): Unit = {
      if (!headWasOpened && name == "body" && xmlNs == XmlNs.html) {
        // Head wasn't opened above. It means
        // programmer didn't include head() in the page.
        underlyingRenderContext.openNode(XmlNs.html, "head")
        upgradeHead()
        underlyingRenderContext.closeNode("head")
        underlyingRenderContext.openNode(xmlNs, name)
      } else if (xmlNs == XmlNs.html && name == "head") {
        headWasOpened = true
        underlyingRenderContext.openNode(xmlNs, name)
        upgradeHead()
      } else {
        underlyingRenderContext.openNode(xmlNs, name)
      }
    }

    override def closeNode(name: String): Unit = {
      underlyingRenderContext.closeNode(name)
    }
  }
}
