import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalPlugin

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
  scalaVersion := "2.11.8", // Need by IntelliJ
  organization := "com.github.fomkin",
  version := "0.3.0-SNAPSHOT",
  libraryDependencies ++= Seq(
    "org.scalatest" %%% "scalatest" % "3.0.1" % "test"
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

lazy val vdomOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("korolev.*;version=${Bundle-Version}")
)

lazy val vdom = crossProject.crossType(CrossType.Pure).
  settings(commonSettings: _*).
  settings(normalizedName := "korolev-vdom").
  enablePlugins(SbtOsgi).settings(vdomOsgiSettings:_*)

lazy val vdomJS = vdom.js
lazy val vdomJVM = vdom.jvm

lazy val serverOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("korolev.server.*;version=${Bundle-Version}")
)

lazy val server = project.
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-server",
    libraryDependencies += "biz.enef" %% "slogging-slf4j" % "0.5.2"
  ).
  dependsOn(korolevJVM).
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

lazy val async = crossProject.crossType(CrossType.Pure).
  settings(commonSettings: _*).
  settings(normalizedName := "korolev-async").
  enablePlugins(SbtOsgi).settings(asyncOsgiSettings:_*)

lazy val asyncJS = async.js
lazy val asyncJVM = async.jvm

lazy val `async-monix` = crossProject.crossType(CrossType.Pure).
  settings(commonSettings: _*).
  settings(normalizedName := "korolev-async-monix").
  enablePlugins(SbtOsgi).settings(asyncOsgiSettings:_*)

lazy val asyncMonixJs = `async-monix`.js.
  dependsOn(asyncJS).
  settings(
    libraryDependencies ++= Seq(
      "io.monix" %%% "monix" % "2.2.3"
    )
  )

lazy val asyncMonixJvm = `async-monix`.jvm.
  dependsOn(asyncJVM).
  settings(
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "2.2.3"
    )
  )

lazy val bridgeOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("bridge.*;version=${Bundle-Version}")
)

lazy val bridge = crossProject.crossType(CrossType.Pure).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-bridge",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % "0.4.4" % "test"
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    unmanagedResourceDirectories in Compile += file("bridge") / "src" / "main" / "resources"
  ).
  dependsOn(async).
  enablePlugins(SbtOsgi).settings(bridgeOsgiSettings:_*)

lazy val bridgeJS = bridge.js
lazy val bridgeJVM = bridge.jvm

lazy val korolevOsgiSettings = osgiSettings ++ Seq(
  OsgiKeys.exportPackage := Seq("korolev.*;version=${Bundle-Version}")
)

lazy val korolev = crossProject.crossType(CrossType.Pure).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev",
    libraryDependencies += "biz.enef" %%% "slogging" % "0.5.2",
    unmanagedResourceDirectories in Compile += file("korolev") / "src" / "main" / "resources"
  ).
  dependsOn(vdom, bridge).
  enablePlugins(SbtOsgi).settings(korolevOsgiSettings:_*)

lazy val korolevJS = korolev.js
lazy val korolevJVM = korolev.jvm

// Examples
val examples = file("examples")

lazy val simpleExample = (project in  examples / "simple").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  dependsOn(`server-blaze`)

lazy val routingExample = (project in examples / "routing").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  dependsOn(`server-blaze`)

lazy val gameOfLifeExample = (project in examples / "game-of-life").
  enablePlugins(JavaAppPackaging).
  enablePlugins(UniversalPlugin).
  settings(exampleSettings: _*).
  dependsOn(`server-blaze`)

val `integration-tests` = project.
  settings(commonSettings).
  settings(dontPublishSettings:_*).
  settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.+",
      "org.seleniumhq.selenium" % "selenium-java" % "2.53.1"
    )
  ).
  dependsOn(`server-blaze`)

lazy val root = project.in(file(".")).
  settings(dontPublishSettings:_*).
  aggregate(
    korolevJS, korolevJVM,
    bridgeJS, bridgeJVM,
    vdomJS, vdomJVM,
    asyncJS, asyncJVM,
    asyncMonixJs, asyncMonixJvm,
    server, `server-blaze`,
    simpleExample, routingExample, gameOfLifeExample,
    `integration-tests`
  )

publishTo := unusedRepo

crossScalaVersions := Seq("2.11.8", "2.12.1")

publishArtifact := false

