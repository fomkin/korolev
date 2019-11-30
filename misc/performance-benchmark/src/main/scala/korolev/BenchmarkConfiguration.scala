package korolev

case class BenchmarkConfiguration(host: String, port: Int, path: String, ssl: Boolean, testers: Int)
