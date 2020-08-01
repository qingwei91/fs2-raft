import com.typesafe.sbt.packager.docker.DockerPermissionStrategy
import sbt.Keys.scalaVersion

lazy val lolipop = project
  .in(file("."))
  .settings(
    name := "lolipop",
    description := "Purely functional Raft implementation",
    organizationName := "io.github.qingwei91",
    micrositeGithubOwner := "qingwei91",
    micrositeGithubRepo := "lolipop",
    micrositeCompilingDocsTool := WithMdoc,
    micrositeDataDirectory := file("docs"),
    micrositeBaseUrl := "/lolipop",
    micrositeDocumentationUrl := "/lolipop/docs",
    micrositeUrl := "https://qingwei91.github.io"
  )
  .enablePlugins(MicrositesPlugin)
  .dependsOn(core, swaydbPersisent, examples)
  .aggregate(core, swaydbPersisent, examples)

lazy val core = project
  .in(file("core"))
  .settings(common)
  .settings(
    libraryDependencies ++= coreDeps
  )

lazy val swaydbPersisent = project
  .in(file("swaydb-persistent"))
  .dependsOn(core)
  .settings(common)
  .settings(
    libraryDependencies ++= Seq(
      "io.swaydb" %% "swaydb" % swaydbVersion
    )
  )

lazy val examples = project
  .in(file("examples"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .dependsOn(core % "test->test;compile->compile", swaydbPersisent)
  .settings(common)
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-dsl"           % http4sVersion,
      "org.http4s"            %% "http4s-blaze-server"  % http4sVersion,
      "org.http4s"            %% "http4s-blaze-client"  % http4sVersion,
      "org.http4s"            %% "http4s-circe"         % http4sVersion,
      "io.circe"              %% "circe-core"           % circeVersion,
      "io.circe"              %% "circe-literal"        % circeVersion,
      "io.circe"              %% "circe-generic"        % circeVersion,
      "io.circe"              %% "circe-parser"         % circeVersion,
      "com.github.pureconfig" %% "pureconfig"           % "0.10.2",
      "io.swaydb"             %% "swaydb"               % swaydbVersion,
      "io.swaydb"             %% "cats-effect"          % swaydbVersion,
      "io.grpc"               % "grpc-netty"            % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb"  %% "scalapb-runtime"      % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.thesamet.scalapb"  %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    ),
    wartremoverExcluded += sourceManaged.value,
    mainClass in ThisBuild := Some("raft.grpc.RaftGrpcExample"),
    mainClass in assembly := Some("raft.grpc.RaftGrpcExample"),
    assemblyMergeStrategy in assembly := {
      case path if path.endsWith("META-INF/INDEX.LIST") => MergeStrategy.concat
      case path if path.endsWith("logback.xml") => MergeStrategy.first
      case path if path.endsWith("META-INF/MANIFEST.MF") => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  )
  .settings(
    maintainer in Docker := "QingWei",
    dockerBaseImage := "openjdk:8-alpine",
    daemonUserUid in Docker := None,
    daemonUser in Docker := "root",
    dockerExposedPorts := Seq(80),
    dockerPermissionStrategy := DockerPermissionStrategy.Run
  )

lazy val http4sVersion = "0.20.0-M5"
lazy val circeVersion  = "0.11.1"
lazy val swaydbVersion = "0.14.2"
lazy val common = Def.settings(
  version := "1.0",
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq(
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    "-Xfuture", // Turn on future language features.
    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match", // Pattern match may not be typesafe.
    "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification", // Enable partial unification in type constructor inference
    "-Ywarn-dead-code", // Warn when dead code is identified.
    "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit", // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals", // Warn if a local definition is unused.
    "-Ywarn-unused:params", // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates", // Warn if a private member is unused.
    "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
  ),
  scalacOptions in Test ++= Seq("-Yrangepos"),
  testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "failtrace"),
  wartremoverErrors ++= Warts.unsafe,
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0")
)
lazy val coreDeps = Seq(
  "org.typelevel"  %% "cats-core"         % "1.6.0",
  "org.typelevel"  %% "cats-free"         % "1.6.0",
  "org.typelevel"  %% "cats-effect"       % "1.2.0",
  "co.fs2"         %% "fs2-core"          % "1.0.4",
  "ch.qos.logback" % "logback-classic"    % "1.2.3",
  "org.slf4j"      % "slf4j-api"          % "1.7.25",
  "org.typelevel"  %% "cats-core"         % "1.6.0" % Test,
  "org.specs2"     %% "specs2-core"       % "4.3.4" % Test,
  "org.specs2"     %% "specs2-scalacheck" % "4.3.4" % Test
)
