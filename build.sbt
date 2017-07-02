import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalPlugin

val levshaVersion = "0.4.2"

val unusedRepo = Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))

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
  scalaVersion := "2.11.11", // Need by IntelliJ
  organization := "com.github.fomkin",
  version := "0.4.2",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  ),
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-Xfatal-warnings",
    "-language:postfixOps",
    "-language:implicitConversions"
  )
)

val exampleSettings = commonSettings ++ dontPublishSettings ++ Seq(
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.+"
)

lazy val serverOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("korolev.server.*;version=${Bundle-Version}")
)

lazy val server = project.
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-server",
    libraryDependencies += "biz.enef" %% "slogging-slf4j" % "0.5.2"
  ).
  dependsOn(korolev).
  enablePlugins(SbtOsgi).settings(serverOsgiSettings:_*)

lazy val serverBlazeOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("korolev.blazeServer.*;version=${Bundle-Version}")
)

lazy val `server-blaze` = project.
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-server-blaze",
    libraryDependencies ++= Seq("org.http4s" %% "blaze-http" % "0.12.4")
  ).
  dependsOn(server).
  enablePlugins(SbtOsgi).settings(serverBlazeOsgiSettings:_*)

lazy val asyncOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("korolev.*;version=${Bundle-Version}")
)

lazy val async = project.
  settings(commonSettings: _*).
  settings(normalizedName := "korolev-async").
  enablePlugins(SbtOsgi).settings(asyncOsgiSettings:_*)

lazy val bridgeOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("bridge.*;version=${Bundle-Version}")
)

lazy val bridge = project.
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-bridge",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % "0.4.4" % "test"
    ),
    testFrameworks += new TestFramework("utest.runner.Framework")
    //unmanagedResourceDirectories in Compile += file("bridge") / "src" / "main" / "resources"
  ).
  dependsOn(async).
  enablePlugins(SbtOsgi).settings(bridgeOsgiSettings:_*)

lazy val korolevOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("korolev.*;version=${Bundle-Version}")
)

lazy val korolev = project.
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev",
    libraryDependencies ++= Seq(
      "biz.enef" %% "slogging" % "0.5.2",
      "com.github.fomkin" %% "levsha-core" % levshaVersion,
      "com.github.fomkin" %% "levsha-events" % levshaVersion
    ),
    unmanagedResourceDirectories in Compile += file("korolev") / "shared" / "src" / "main" / "resources"
  ).
  dependsOn(bridge).
  enablePlugins(SbtOsgi).settings(korolevOsgiSettings:_*)

val `jcache-support` = project.
  enablePlugins(SbtOsgi).
  settings(commonSettings: _*).
  settings(osgiSettings: _*).
  settings(
    normalizedName := "korolev-jcache-support",
    libraryDependencies += "javax.cache" % "cache-api" % "1.0.0",
    OsgiKeys.exportPackage := Seq("korolev.server.jcache.*;version=${Bundle-Version}")
  ).
  dependsOn(server)

// Examples
val examples = file("examples")

lazy val simpleExample = (project in examples / "simple").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  settings(mainClass := Some("SimpleExample")).
  dependsOn(`server-blaze`)

lazy val routingExample = (project in examples / "routing").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  settings(mainClass := Some("RoutingExample")).
  dependsOn(`server-blaze`)

lazy val gameOfLifeExample = (project in examples / "game-of-life").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  settings(mainClass := Some("GameOfLife")).
  dependsOn(`server-blaze`)

lazy val jcacheExample = (project in examples / "jcache").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  settings(
    mainClass := Some("JCacheExample"),
    libraryDependencies += "com.hazelcast" % "hazelcast" % "3.8"
  ).
  dependsOn(`server-blaze`, `jcache-support`)

lazy val formDataExample = (project in examples / "form-data").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  settings(mainClass := Some("FormDataExample")).
  dependsOn(`server-blaze`)

lazy val delayExample = (project in examples / "delay").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  settings(mainClass := Some("DelayExample")).
  dependsOn(`server-blaze`)

lazy val webComponentExample = (project in examples / "web-component").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  settings(mainClass := Some("WebComponentExample")).
  dependsOn(`server-blaze`)

lazy val `integration-tests` = project.
  settings(commonSettings).
  settings(dontPublishSettings:_*).
  settings(
    fork in run := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.+",
      "org.seleniumhq.selenium" % "selenium-java" % "2.53.1"
    )
  ).
  dependsOn(`server-blaze`)

lazy val `performance-benchmark` = project.
  settings(commonSettings).
  settings(dontPublishSettings:_*).
  settings(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    fork in run := true,
    libraryDependencies ++= Seq(
      "com.spinoco" %% "fs2-http" % "0.1.7",
      "com.github.fomkin" %% "pushka-json" % "0.8.0"
    )
  )

lazy val root = project.in(file(".")).
  settings(dontPublishSettings:_*).
  aggregate(
    korolev, bridge, async, server,
    `server-blaze`, `jcache-support`,
    simpleExample, routingExample, gameOfLifeExample,
    jcacheExample, formDataExample, delayExample,
    webComponentExample,
    `integration-tests`, `performance-benchmark`
  )

publishTo := unusedRepo

crossScalaVersions := Seq("2.11.11", "2.12.2")

publishArtifact := false

