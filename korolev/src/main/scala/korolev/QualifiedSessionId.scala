package korolev

case class QualifiedSessionId(deviceId: String, id: String) {
  override lazy val toString: String = s"$deviceId-$id"
}
