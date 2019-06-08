import xerial.sbt.Sonatype._

val levshaVersion = "0.7.2"

val unusedRepo = Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))

val crossVersionSettings = Seq(
  crossScalaVersions := Seq("2.11.12", "2.12.8")
)

val dontPublishSettings = Seq(
  publish := {},
  publishTo := unusedRepo,
  publishArtifact := false,
  headerLicense := None
)

val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := sonatypePublishTo.value,
  sonatypeProjectHosting := Some(GitHubHosting("fomkin", "korolev", "Aleksey Fomkin", "aleksey.fomkin@gmail.com")),
  headerLicense := Some(HeaderLicense.ALv2("2017-2018", "Aleksey Fomkin")),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
)

val commonSettings = publishSettings ++ Seq(
  git.useGitDescribe := true,
  organization := "com.github.fomkin",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  ),
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-Xfatal-warnings",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture",
    "-Ywarn-unused-import"
  )
)

val exampleSettings = commonSettings ++ dontPublishSettings ++ Seq(
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.+"
)

lazy val server = (project in file("server") / "base").
  enablePlugins(GitVersioning).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(normalizedName := "korolev-server").
  dependsOn(korolev)

lazy val `server-akkahttp` = (project in file("server") / "akkahttp").
  enablePlugins(GitVersioning).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-server-akkahttp",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.19",
      "com.typesafe.akka" %% "akka-stream" % "2.5.19",
      "com.typesafe.akka" %% "akka-http" % "10.1.6"
    )
  ).
  dependsOn(server)

lazy val async = project.
  enablePlugins(GitVersioning).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(normalizedName := "korolev-async")

lazy val korolev = project.
  enablePlugins(GitVersioning).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev",
    libraryDependencies ++= Seq(
      "com.github.fomkin" %% "levsha-core" % levshaVersion,
      "com.github.fomkin" %% "levsha-events" % levshaVersion
    ),
    resourceGenerators in Compile += Def
      .task {
        val source = baseDirectory.value / "src" / "main" / "es6"
        val target = (resourceManaged in Compile).value / "static"
        val log = streams.value.log
        JsUtils.assembleJs(source, target, log)
      }
      .taskValue
  ).
  dependsOn(async)

// Contribs

lazy val `slf4j-support` = project.
  enablePlugins(GitVersioning).
  in(file("contrib/slf4j")).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-slf4j-support",
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
  ).
  dependsOn(async)

lazy val `cats-effect-support` = project.
  enablePlugins(GitVersioning).
  in(file("contrib/cats-effect")).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-cats-effect-support",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "1.2.0"
  ).
  dependsOn(async)

// Examples
val examples = file("examples")

lazy val simpleExample = (project in examples / "simple").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("SimpleExample")).
  dependsOn(`server-akkahttp`)

lazy val routingExample = (project in examples / "routing").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("RoutingExample")).
  dependsOn(`server-akkahttp`)

lazy val gameOfLifeExample = (project in examples / "game-of-life").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("GameOfLife")).
  dependsOn(`server-akkahttp`)

lazy val formDataExample = (project in examples / "form-data").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("FormDataExample")).
  dependsOn(`server-akkahttp`)

lazy val `file-streaming-example` = (project in examples / "file-streaming-example").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("FileStreamingExample")).
  dependsOn(`server-akkahttp`)

lazy val delayExample = (project in examples / "delay").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("DelayExample")).
  dependsOn(`server-akkahttp`)

lazy val focusExample = (project in examples / "focus").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("FocusExample")).
  dependsOn(`server-akkahttp`)

lazy val webComponentExample = (project in examples / "web-component").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("WebComponentExample")).
  dependsOn(`server-akkahttp`)

lazy val componentExample = (project in examples / "component").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("ComponentExample")).
  dependsOn(`server-akkahttp`)

lazy val akkaHttpExample = (project in examples / "akka-http").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("AkkaHttpExample")).
  dependsOn(`server-akkahttp`)

lazy val monixExample = (project in examples / "monix").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("MonixExample")).
  settings(
    libraryDependencies += "io.monix" %% "monix-eval" % "3.0.0-RC2"
  ).
  dependsOn(`cats-effect-support`, `server-akkahttp`)

lazy val eventDataExample = (project in examples / "event-data").
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("EventDataExample")).
  dependsOn(`server-akkahttp`)

lazy val `integration-tests` = project.
  disablePlugins(HeaderPlugin).
  settings(crossVersionSettings).
  settings(commonSettings).
  settings(dontPublishSettings:_*).
  settings(
    fork in run := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.+",
      "org.seleniumhq.selenium" % "selenium-java" % "2.53.1",
      "com.github.fomkin" %% "pushka-json" % "0.8.0"
    )
  ).
  dependsOn(`slf4j-support`).
  dependsOn(`server-akkahttp`)

lazy val `performance-benchmark` = project.
  disablePlugins(HeaderPlugin).
  settings(commonSettings).
  settings(dontPublishSettings:_*).
  settings(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    scalaVersion := "2.12.8",
    fork in run := true,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.1.6",
      "com.typesafe.akka" %% "akka-stream" % "2.5.19",
      "com.typesafe.akka" %% "akka-actor"  % "2.5.19",
      "com.typesafe.akka" %% "akka-typed" % "2.5.8",
      "com.github.fomkin" %% "pushka-json" % "0.8.0"
    )
  ).
  dependsOn(korolev)

lazy val root = project.in(file(".")).
  settings(crossVersionSettings).
  disablePlugins(HeaderPlugin).
  settings(dontPublishSettings:_*).
  aggregate(
    korolev, async,
    server, `server-akkahttp`,
    `cats-effect-support`, `slf4j-support`,
    simpleExample, routingExample, gameOfLifeExample,
    formDataExample, `file-streaming-example`, delayExample, focusExample,
    webComponentExample, componentExample, akkaHttpExample,
    eventDataExample, `integration-tests`
  )

dontPublishSettings
