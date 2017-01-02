package korolev.server

import korolev.{Async, Render, VDom}

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class KorolevServiceConfig[F[+_]: Async, S](
  stateStorage: StateStorage[F, S],
  serverRouter: ServerRouter[F, S],
  render: Render[S] = PartialFunction.empty,
  head: VDom.Node = VDom.Node("head", Nil, Nil, Nil)
)
