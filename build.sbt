import xerial.sbt.Sonatype._

val scala2_12Version = "2.12.14"
val scala2_13Version = "2.13.6"
val scala3Version = "3.0.2"

val levshaVersion = "1.1.0"

val akkaVersion = "2.6.14"
val akkaHttpVersion = "10.2.4"
val http4sVersion = "0.21.24" // Scala 3 is not supported in final releases yet

val circeVersion = "0.14.1"
val cats2Version = "2.5.3"

val zioVersion = "1.0.11"
val zioHttpVersion = "1.0.0.0-RC17"

val fs2_2Version = "2.5.6"
val monixVersion = "3.4.0"
val scodecVersion = "1.1.27"

val unusedRepo = Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))

val crossVersionSettings = Seq(
  crossScalaVersions := Seq(scala2_12Version, scala2_13Version, scala3Version)
)

val dontPublishSettings = Seq(
  publish := {},
  publishTo := unusedRepo,
  publishArtifact := false,
  headerLicense := None
)

val publishSettings = Seq(
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  publishTo := sonatypePublishTo.value,
  sonatypeProfileName := "org.fomkin",
  sonatypeProjectHosting := Some(GitHubHosting("fomkin", "korolev", "Aleksey Fomkin", "aleksey.fomkin@gmail.com")),
  headerLicense := Some(HeaderLicense.ALv2("2017-2020", "Aleksey Fomkin")),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
)

val commonSettings = publishSettings ++ Seq(
  git.useGitDescribe := true,
  organization := "org.fomkin",
  // Add Scala 2 compiler plugins
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
          compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full),
        )
      case _ => Seq()
    }
  },
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.9" % Test,
    "org.scalatestplus" %% "scalacheck-1-15" % "3.2.9.0" % Test
  ),
  //javaOptions in Test += "-XX:-OmitStackTraceInFastThrow",
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "-deprecation",
          "-feature",
          "-language:postfixOps",
          "-language:implicitConversions",
          "-language:higherKinds",
          "-Xlint",
          "-Ywarn-numeric-widen",
          "-Ywarn-value-discard",
          "-Xsource:3",
          //"-P:kind-projector:underscore-placeholders",
        )
      case _ =>
        Seq(
          "-deprecation",
          "-feature",
          "-language:postfixOps",
          "-language:implicitConversions",
          "-language:higherKinds",
          "-Ykind-projector"
        )
    }
  }
)

val exampleSettings = commonSettings ++ dontPublishSettings ++ Seq(
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.+"
)

val modules = file("modules")
val interop = file("interop")
val examples = file("examples")
val misc = file("misc")

lazy val bytes = project
  .in(modules / "bytes")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-bytes"
  )

lazy val effect = project
  .in(modules / "effect")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-effect"
  )
  .dependsOn(bytes)

lazy val web = project
  .in(modules / "web")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    description := "Collection of data classes for Web Standards support",
    normalizedName := "korolev-web"
  )

lazy val http = project
  .in(modules / "http")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-http",
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" %% "akka-actor-typed" % akkaVersion % Test).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-stream" % akkaVersion % Test).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-http" % akkaHttpVersion % Test).cross(CrossVersion.for3Use2_13),
    )
  )
  .dependsOn(effect, web)

lazy val korolev = project
  .in(modules / "korolev")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev",
    libraryDependencies ++= Seq(
      "com.github.fomkin" %% "levsha-core" % levshaVersion,
      "com.github.fomkin" %% "levsha-events" % levshaVersion
    ),
    Compile / resourceGenerators += Def
      .task {
        val source = baseDirectory.value / "src" / "main" / "es6"
        val target = (Compile / resourceManaged).value / "static"
        val log = streams.value.log
        JsUtils.assembleJs(source, target, log)
      }
      .taskValue
  )
  .dependsOn(effect, web)

lazy val standalone = project
  .in(modules / "standalone")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-standalone"
  )
  .dependsOn(korolev, http)

lazy val testkit = project
  .in(modules / "testkit")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-testkit",
    libraryDependencies += "org.graalvm.js" % "js" % "20.3.0"
  )
  .dependsOn(korolev)

// Interop

lazy val akka = project
  .in(interop / "akka")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-akka",
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" %% "akka-actor" % akkaVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-stream" % akkaVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-http" % akkaHttpVersion).cross(CrossVersion.for3Use2_13)
    )
  )
  .dependsOn(korolev)

lazy val http4s = project
  .in(interop / "http4s")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-http4s",
    libraryDependencies ++= Seq(
      ("org.http4s"     %% "http4s-server" % http4sVersion).cross(CrossVersion.for3Use2_13),
      ("org.http4s"     %% "http4s-dsl"    % http4sVersion).cross(CrossVersion.for3Use2_13)
    )
  )
  .dependsOn(korolev, web, fs2, scodec)

lazy val zioHttp = project
  .in(interop / "zio-http")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-zio-http",
    libraryDependencies ++= Seq(
      ("io.d11" %% "zhttp" % zioHttpVersion).cross(CrossVersion.for3Use2_13)
    )
  )
  .dependsOn(korolev, web, zio, zioStreams)

lazy val slf4j = project.
  in(interop / "slf4j").
  enablePlugins(GitVersioning).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-slf4j",
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
  ).
  dependsOn(effect)

lazy val cats = project.
  in(interop / "cats").
  enablePlugins(GitVersioning).
  settings(crossVersionSettings).
  settings(commonSettings: _*).
  settings(
    normalizedName := "korolev-cats",
    libraryDependencies += "org.typelevel" %% "cats-effect" % cats2Version
  ).
  dependsOn(effect)

lazy val monix = project
  .in(interop / "monix")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-monix",
    libraryDependencies ++= List(
      "io.monix" %% "monix-eval" % monixVersion,
      "io.monix" %% "monix-execution" % monixVersion
    )
  )
  .dependsOn(effect)

lazy val zio = project
  .in(interop / "zio")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-zio",
    libraryDependencies += "dev.zio" %% "zio" % zioVersion
  )
  .dependsOn(effect)

lazy val zioStreams = project
  .in(interop / "zio-streams")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-zio-streams",
    libraryDependencies += "dev.zio" %% "zio-streams" % zioVersion
  )
  .dependsOn(effect, zio)

lazy val fs2 = project
  .in(interop / "fs2")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-fs2",
    libraryDependencies += "co.fs2" %% "fs2-core" % fs2_2Version
  )
  .dependsOn(effect, cats)

lazy val scodec = project
  .in(interop / "scodec")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-scodec",
    libraryDependencies += "org.scodec" %% "scodec-bits" % scodecVersion
  )
  .dependsOn(bytes)


// Examples

lazy val simpleExample = (project in examples / "simple")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("SimpleExample"))
  .dependsOn(akka)

lazy val routingExample = project
  .in(examples / "routing")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("RoutingExample"))
  .dependsOn(akka)

lazy val gameOfLifeExample = project
  .in(examples / "game-of-life")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("GameOfLife"))
  .dependsOn(akka)

lazy val formDataExample = project
  .in(examples / "form-data")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("FormDataExample"))
  .dependsOn(akka)

lazy val `file-streaming-example` = project
  .in(examples / "file-streaming")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("FileStreamingExample"))
  .dependsOn(akka, monix)

lazy val delayExample = project
  .in(examples / "delay")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("DelayExample"))
  .dependsOn(akka)

lazy val focusExample = project
  .in(examples / "focus")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("FocusExample"))
  .dependsOn(akka)

lazy val webComponentExample = project
  .in(examples / "web-component")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("WebComponentExample"))
  .dependsOn(akka)

lazy val componentExample = project
  .in(examples / "component")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("ComponentExample"))
  .dependsOn(akka)

lazy val akkaHttpExample = project
  .in(examples / "akka-http")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("AkkaHttpExample"))
  .dependsOn(akka)

lazy val http4sZioExample = project
  .in(examples / "http4s-zio")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("Http4sZioExample"))
  .settings(libraryDependencies += "dev.zio" %% "zio-interop-cats" % "2.5.1.0")
  .settings(libraryDependencies += ("org.http4s" %% "http4s-blaze-server" % http4sVersion).cross(CrossVersion.for3Use2_13))
  .dependsOn(zio, http4s)

lazy val zioHttpExample = project
  .in(examples / "zio-http")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("ZioHttpExample"))
  .dependsOn(zio, zioHttp)

lazy val catsEffectExample = project
  .in(examples / "cats")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("CatsIOExample"))
  .dependsOn(cats, akka)

lazy val zioExample = project
  .in(examples / "zio")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("ZioExample"))
  .dependsOn(zio, akka, testkit % Test)

lazy val monixExample = project
  .in(examples / "monix")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("MonixExample"))
  .dependsOn(monix, akka)

lazy val eventDataExample = project
  .in(examples / "event-data")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("EventDataExample"))
  .dependsOn(akka)

lazy val evalJsExample = project
  .in(examples / "evalJs")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("EvalJsExample"))
  .dependsOn(akka)

lazy val contextScopeExample = project
  .in(examples / "context-scope")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("ContextScopeExample"))
  .dependsOn(akka)

lazy val extensionExample = project
  .in(examples / "extension")
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(exampleSettings: _*)
  .settings(mainClass := Some("ExtensionExample"))
  .dependsOn(akka)

// Misc

lazy val `integration-tests` = project
  .in(misc / "integration-tests")
  .disablePlugins(HeaderPlugin)
  //.settings(crossVersionSettings)
  .settings(commonSettings)
  .settings(dontPublishSettings:_*)
  .settings(
    scalaVersion := scala2_13Version,
    run / fork := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.+",
      "org.seleniumhq.selenium" % "selenium-java" % "2.53.1",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    )
  )
  .dependsOn(slf4j)
  .dependsOn(akka)

lazy val `performance-benchmark` = project
  .in(misc / "performance-benchmark")
  .disablePlugins(HeaderPlugin)
  .settings(commonSettings)
  .settings(dontPublishSettings:_*)
  .settings(
    scalaVersion := scala2_13Version,
    run / fork := true,
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" %% "akka-http" % akkaHttpVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-stream" % akkaVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-actor"  % akkaVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-actor-typed" % akkaVersion).cross(CrossVersion.for3Use2_13),
      "com.lihaoyi" %% "ujson" % "1.3.15"
    )
  )
  .dependsOn(korolev)

lazy val root = project
  .in(file("."))
  .disablePlugins(HeaderPlugin)
  .settings(crossVersionSettings)
  .settings(dontPublishSettings:_*)
  .settings(name := "Korolev Project")
  .aggregate(
    korolev, effect, web, http, standalone, testkit,
    bytes,
    // Interop
    akka, cats, monix, zio, zioStreams, slf4j,
    scodec, fs2, http4s, zioHttp,
    // Examples
    simpleExample, routingExample, gameOfLifeExample,
    formDataExample, `file-streaming-example`, delayExample,
    focusExample, webComponentExample, componentExample,
    akkaHttpExample, contextScopeExample, eventDataExample,
    extensionExample, zioExample, monixExample,
    catsEffectExample, evalJsExample, http4sZioExample,
    zioHttpExample
  )

