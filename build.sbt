val levshaVersion = "0.6.1"

val unusedRepo = Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))

val crossVersionSettings = Seq(
  crossScalaVersions := Seq("2.11.12", "2.12.4")
)

val dontPublishSettings = Seq(
  publish := {},
  publishTo := unusedRepo,
  publishArtifact := false
)

val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at s"${nexus}content/repositories/snapshots")
    else Some("releases" at s"${nexus}service/local/staging/deploy/maven2")
  },
  pomExtra := {
    <url>https://github.com/fomkin/korolev</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>http://apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:fomkin/korolev.git</url>
      <connection>scm:git:git@github.com:fomkin/korolev.git</connection>
    </scm>
    <developers>
      <developer>
        <id>fomkin</id>
        <name>Aleksey Fomkin</name>
        <email>aleksey.fomkin@gmail.com</email>
      </developer>
    </developers>
  }
)

val commonSettings = publishSettings ++ Seq(
  organization := "com.github.fomkin",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.4" % Test
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
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-server",
    libraryDependencies += "biz.enef" %% "slogging-slf4j" % "0.6.0"
  ).
  dependsOn(korolev)

lazy val `server-blaze` = (project in file("server") / "blaze").
  settings(commonSettings: _*).
  settings(crossVersionSettings).
  settings(
    normalizedName := "korolev-server-blaze",
    libraryDependencies ++= Seq("org.http4s" %% "blaze-http" % "0.12.11")
  ).
  dependsOn(server)

lazy val `server-akkahttp` = (project in file("server") / "akkahttp").
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-server-akkahttp",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.8",
      "com.typesafe.akka" %% "akka-stream" % "2.5.8",
      "com.typesafe.akka" %% "akka-http" % "10.0.11"
    )
  ).
  dependsOn(server)

lazy val async = project.
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(normalizedName := "korolev-async")

lazy val korolev = project.
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev",
    libraryDependencies ++= Seq(
      "biz.enef" %% "slogging" % "0.6.0",
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

lazy val `jcache-support` = project.
  in(file("contrib/jcache")).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-jcache-support",
    libraryDependencies += "javax.cache" % "cache-api" % "1.0.0"
  ).
  dependsOn(server)

lazy val `monix-support` = project.
  in(file("contrib/monix")).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-monix-support",
    libraryDependencies += "io.monix" %% "monix-eval" % "3.0.0-M3"
  ).
  dependsOn(async)

// Examples
val examples = file("examples")

lazy val simpleExample = (project in examples / "simple").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("SimpleExample")).
  dependsOn(`server-blaze`)

lazy val routingExample = (project in examples / "routing").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("RoutingExample")).
  dependsOn(`server-blaze`)

lazy val gameOfLifeExample = (project in examples / "game-of-life").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("GameOfLife")).
  dependsOn(`server-blaze`)

lazy val jcacheExample = (project in examples / "jcache").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(
    mainClass := Some("JCacheExample"),
    libraryDependencies += "com.hazelcast" % "hazelcast" % "3.8"
  ).
  dependsOn(`server-blaze`, `jcache-support`)

lazy val formDataExample = (project in examples / "form-data").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("FormDataExample")).
  dependsOn(`server-blaze`)

lazy val delayExample = (project in examples / "delay").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("DelayExample")).
  dependsOn(`server-blaze`)

lazy val focusExample = (project in examples / "focus").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("FocusExample")).
  dependsOn(`server-blaze`)

lazy val webComponentExample = (project in examples / "web-component").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("WebComponentExample")).
  dependsOn(`server-blaze`)

lazy val componentExample = (project in examples / "component").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("ComponentExample")).
  dependsOn(`server-blaze`)

lazy val akkaHttpExample = (project in examples / "akka-http").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("AkkaHttpExample")).
  dependsOn(`server-akkahttp`)

lazy val monixExample = (project in examples / "monix").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("MonixExample")).
  dependsOn(`monix-support`, `server-akkahttp`)

lazy val eventDataExample = (project in examples / "event-data").
  settings(crossVersionSettings).
  settings(exampleSettings: _*).
  settings(mainClass := Some("EventDataExample")).
  dependsOn(`server-blaze`)

lazy val `integration-tests` = project.
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
  dependsOn(`server-blaze`).
  dependsOn(`server-akkahttp`)

lazy val `performance-benchmark` = project.
  settings(commonSettings).
  settings(dontPublishSettings:_*).
  settings(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    scalaVersion := "2.12.4",
    fork in run := true,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.0.11",
      "com.typesafe.akka" %% "akka-stream" % "2.5.8",
      "com.typesafe.akka" %% "akka-actor"  % "2.5.8",
      "com.typesafe.akka" %% "akka-typed" % "2.5.8",
      "com.github.fomkin" %% "pushka-json" % "0.8.0"
    )
  ).
  dependsOn(korolev)

lazy val root = project.in(file(".")).
  settings(crossVersionSettings).
  settings(dontPublishSettings:_*).
  aggregate(
    korolev, async,
    server, `server-blaze`, `server-akkahttp`,
    `jcache-support`, `monix-support`,
    simpleExample, routingExample, gameOfLifeExample,
    jcacheExample, formDataExample, delayExample, focusExample,
    webComponentExample, componentExample, akkaHttpExample,
    eventDataExample, `integration-tests`
  )

dontPublishSettings
