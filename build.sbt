import xerial.sbt.Sonatype._

val levshaVersion = "0.10.0"

val unusedRepo = Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))

val crossVersionSettings = Seq(
  crossScalaVersions := Seq("2.12.12", "2.13.4")
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
  sonatypeProfileName := "org.fomkin",
  sonatypeProjectHosting := Some(GitHubHosting("fomkin", "korolev", "Aleksey Fomkin", "aleksey.fomkin@gmail.com")),
  headerLicense := Some(HeaderLicense.ALv2("2017-2020", "Aleksey Fomkin")),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
)

val commonSettings = publishSettings ++ Seq(
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.2" cross CrossVersion.full),
  git.useGitDescribe := true,
  organization := "org.fomkin",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    "org.scalacheck" %% "scalacheck" % "1.14.1" % Test
  ),
  //javaOptions in Test += "-XX:-OmitStackTraceInFastThrow",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Xlint",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )
)

val exampleSettings = commonSettings ++ dontPublishSettings ++ Seq(
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.+"
)

val modules = file("modules")
val interop = file("interop")
val examples = file("examples")
val misc = file("misc")


lazy val effect = project
  .in(modules / "effect")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-effect"
  )

lazy val web = project
  .in(modules / "web")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    description := "Collection of data classes for Web Standard support",
    normalizedName := "korolev-web"
  )

lazy val http = project
  .in(modules / "http")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-http"
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
    resourceGenerators in Compile += Def
      .task {
        val source = baseDirectory.value / "src" / "main" / "es6"
        val target = (resourceManaged in Compile).value / "static"
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
      "com.typesafe.akka" %% "akka-actor" % "2.6.10",
      "com.typesafe.akka" %% "akka-stream" % "2.6.10",
      "com.typesafe.akka" %% "akka-http" % "10.2.1"
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
      "org.http4s"     %% "http4s-server" % "0.21.14",
      "org.http4s"     %% "http4s-dsl"          % "0.21.14"
    )
  )
  .dependsOn(korolev, web, fs2)

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
    libraryDependencies += "org.typelevel" %% "cats-effect" % "2.3.1"
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
      "io.monix" %% "monix-eval" % "3.1.0",
      "io.monix" %% "monix-execution" % "3.1.0"
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
    libraryDependencies += "dev.zio" %% "zio" % "1.0.0"
  )
  .dependsOn(effect)

lazy val fs2 = project
  .in(interop / "fs2")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-fs2",
    libraryDependencies += "co.fs2" %% "fs2-core" % "2.5.0"
  )
  .dependsOn(effect, cats)

lazy val scodec = project
  .in(interop / "scodec")
  .enablePlugins(GitVersioning)
  .settings(crossVersionSettings)
  .settings(commonSettings: _*)
  .settings(
    normalizedName := "korolev-scodec",
    libraryDependencies += "org.scodec" %% "scodec-bits" % "1.1.18"
  )
  .dependsOn(effect)


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
  .settings(libraryDependencies += "dev.zio" %% "zio-interop-cats" % "2.1.4.1")
  .settings(libraryDependencies +=  "org.http4s" %% "http4s-blaze-server" % "0.21.14")
  .dependsOn(zio, http4s)

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
  .settings(crossVersionSettings)
  .settings(commonSettings)
  .settings(dontPublishSettings:_*)
  .settings(
    fork in run := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.+",
      "org.seleniumhq.selenium" % "selenium-java" % "2.53.1",
      "io.circe" %% "circe-core" % "0.12.2",
      "io.circe" %% "circe-generic" % "0.12.2",
      "io.circe" %% "circe-parser" % "0.12.2"
    )
  )
  .dependsOn(slf4j)
  .dependsOn(akka)

lazy val `performance-benchmark` = project
  .in(misc / "performance-benchmark")
  .disablePlugins(HeaderPlugin)
  .settings(commonSettings)
  .settings(dontPublishSettings:_*)
  .settings(crossVersionSettings)
  .settings(
    fork in run := true,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.2.0",
      "com.typesafe.akka" %% "akka-stream" % "2.6.8",
      "com.typesafe.akka" %% "akka-actor"  % "2.6.8",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.6.8",
      "com.lihaoyi" %% "ujson" % "0.9.5"
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
    // Interop
    akka, cats, monix, zio, slf4j,
    scodec, fs2, http4s,
    // Examples
    simpleExample, routingExample, gameOfLifeExample,
    formDataExample, `file-streaming-example`, delayExample,
    focusExample, webComponentExample, componentExample,
    akkaHttpExample, contextScopeExample, eventDataExample,
    extensionExample, zioExample, monixExample,
    catsEffectExample, evalJsExample, http4sZioExample,
    // Misc
    `performance-benchmark`, `integration-tests`
  )

