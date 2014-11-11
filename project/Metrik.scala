import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import spray.revolver.RevolverPlugin._
import sbt.Defaults.itSettings
import sbtassembly.Plugin._
import sbt.Keys._
import sbt._

object Metrik extends Build {

  import Dependencies._
  import MultiJVM._
  import Settings._
  import Packager._

  lazy val root = Project("root", file("."))
    .aggregate(metrikCore)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(eclipseSettings:_*)
    .settings(noPublishing: _*)

  lazy val metrikCore =
    Project("metrik-core", file("metrik-core"))
      .settings(basicSettings: _*)
      .settings(formatSettings: _*)
      .configs(IntegrationTest)
      .configs(MultiJvm)
      .settings(itSettings: _*)
      .settings(itExtraSettings: _*)
      .settings(multiJvmSettings: _*)
      .settings(Revolver.settings:_*)
      .settings(assemblySettings: _*)
  	  .settings(packagerSettings: _*)
      .settings(
        libraryDependencies ++=
          compile(sprayClient, sprayCan, sprayRouting, sprayTestKit, sprayJson, akkaActor, akkaTestKit, akkaRemote, akkaCluster, akkaContrib, multiNodeTestKit, scalaTest, akkaQuartz,
            hdrHistogram, specs2, mockito, astyanaxCore, astyanaxThrift, astyanaxCassandra, kryo, scalaLogging, slf4j, logbackClassic, commonsLang, akkaSlf4j) ++
          test(sprayTestKit, akkaTestKit, multiNodeTestKit, scalaTest, specs2, mockito) ++
          it(scalaTest)
      )

  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)

}