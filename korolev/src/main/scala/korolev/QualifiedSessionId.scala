package korolev

import korolev.state.{DeviceId, SessionId}

case class QualifiedSessionId(deviceId: DeviceId, id: SessionId) {
  override lazy val toString: String = s"$deviceId-$id"
}
