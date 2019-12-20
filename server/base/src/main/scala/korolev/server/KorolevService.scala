package korolev.server

trait KorolevService[F[_]] {

  /**
    * Process HTTP request
    */
  def http(request: Request.Http[F]): F[Response.Http[F]]

  /**
    * Process WebSocket requests
    */
  def ws(request: Request.WebSocket[F]): F[Response.WebSocket[F]]
}