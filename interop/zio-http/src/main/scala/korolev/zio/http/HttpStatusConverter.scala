package korolev.zio.http

import korolev.web.Response.{Status => KStatus}
import zhttp.http.Status
import zhttp.http.Status._

object HttpStatusConverter {

  def fromKorolevStatus(kStatus: KStatus): Status = (kStatus.code: @unchecked) match {
    case 100 =>
       CONTINUE
    case 101 =>
       SWITCHING_PROTOCOLS
    case 102 =>
       PROCESSING
    case 200 =>
       OK
    case 201 =>
       CREATED
    case 202 =>
       ACCEPTED
    case 203 =>
       NON_AUTHORITATIVE_INFORMATION
    case 204 =>
       NO_CONTENT
    case 205 =>
       RESET_CONTENT
    case 206 =>
       PARTIAL_CONTENT
    case 207 =>
       MULTI_STATUS
    case 300 =>
       MULTIPLE_CHOICES
    case 301 =>
       MOVED_PERMANENTLY
    case 302 =>
       FOUND
    case 303 =>
       SEE_OTHER
    case 304 =>
       NOT_MODIFIED
    case 305 =>
       USE_PROXY
    case 307 =>
       TEMPORARY_REDIRECT
    case 308 =>
       PERMANENT_REDIRECT
    case 400 =>
       BAD_REQUEST
    case 401 =>
       UNAUTHORIZED
    case 402 =>
       PAYMENT_REQUIRED
    case 403 =>
       FORBIDDEN
    case 404 =>
       NOT_FOUND
    case 405 =>
       METHOD_NOT_ALLOWED
    case 406 =>
       NOT_ACCEPTABLE
    case 407 =>
       PROXY_AUTHENTICATION_REQUIRED
    case 408 =>
       REQUEST_TIMEOUT
    case 409 =>
       CONFLICT
    case 410 =>
       GONE
    case 411 =>
       LENGTH_REQUIRED
    case 412 =>
       PRECONDITION_FAILED
    case 413 =>
       REQUEST_ENTITY_TOO_LARGE
    case 414 =>
       REQUEST_URI_TOO_LONG
    case 415 =>
       UNSUPPORTED_MEDIA_TYPE
    case 416 =>
       REQUESTED_RANGE_NOT_SATISFIABLE
    case 417 =>
       EXPECTATION_FAILED
    case 421 =>
       MISDIRECTED_REQUEST
    case 422 =>
       UNPROCESSABLE_ENTITY
    case 423 =>
       LOCKED
    case 424 =>
       FAILED_DEPENDENCY
    case 425 =>
       UNORDERED_COLLECTION
    case 426 =>
       UPGRADE_REQUIRED
    case 428 =>
       PRECONDITION_REQUIRED
    case 429 =>
       TOO_MANY_REQUESTS
    case 431 =>
       REQUEST_HEADER_FIELDS_TOO_LARGE
    case 500 =>
       INTERNAL_SERVER_ERROR
    case 501 =>
       NOT_IMPLEMENTED
    case 502 =>
       BAD_GATEWAY
    case 503 =>
       SERVICE_UNAVAILABLE
    case 504 =>
       GATEWAY_TIMEOUT
    case 505 =>
       HTTP_VERSION_NOT_SUPPORTED
    case 506 =>
       VARIANT_ALSO_NEGOTIATES
    case 507 =>
       INSUFFICIENT_STORAGE
    case 510 =>
       NOT_EXTENDED
    case 511 =>
       NETWORK_AUTHENTICATION_REQUIRED

    case _ =>
      INTERNAL_SERVER_ERROR
  }

}
