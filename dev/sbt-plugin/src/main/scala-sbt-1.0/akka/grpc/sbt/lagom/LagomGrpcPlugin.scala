/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.grpc.sbt.lagom

import com.lightbend.lagom.sbt.{ LagomJava, LagomScala }
import sbt.{ AllRequirements, AutoPlugin, Compile, File, PluginTrigger, Plugins, Test }
import akka.grpc.sbt.AkkaGrpcPlugin
import sbt.Keys.sourceManaged

object LagomScalaGrpcPlugin extends LagomGrpcPlugin[LagomScala.type] {
  import AkkaGrpcPlugin.autoImport._
  override lazy val defaultLagomLanguage: AkkaGrpc.Language = AkkaGrpc.Scala
  override lazy val requiredLagomPlugin: AutoPlugin = LagomScala
}

object LagomJavaGrpcPlugin extends LagomGrpcPlugin[LagomScala.type] {
  import AkkaGrpcPlugin.autoImport._
  override lazy val defaultLagomLanguage: AkkaGrpc.Language = AkkaGrpc.Java
  override lazy val requiredLagomPlugin: AutoPlugin = LagomJava
}

trait LagomGrpcPlugin[ThePlugin <: AutoPlugin] extends AutoPlugin {
  import AkkaGrpcPlugin.autoImport._
  def defaultLagomLanguage: AkkaGrpc.Language
  def requiredLagomPlugin: AutoPlugin

  override def trigger: PluginTrigger = AllRequirements

  override def requires: Plugins = requiredLagomPlugin && AkkaGrpcPlugin

  override def projectSettings: Seq[sbt.Setting[_]] = defaultSettings

  private def defaultSettings =
    Seq(
      akkaGrpcGeneratedLanguages := Seq(defaultLagomLanguage),
      privateExtraGrpcTargets := lagomTargets
    )

  private val lagomTargets: (File, Seq[String]) => Seq[protocbridge.Target] = { (targetPath, settings) =>
    Seq(
      protocbridge.Target(
        LagomGenerator.generator(LagomScalaClientCodeGenerator),
        targetPath,
        settings
      )
    )
  }

}

// This hack give us visiblity over `ProtocBridgeSbtPluginCodeGenerator` which is `private[akka]`
object LagomGenerator {
  def generator(impl: akka.grpc.gen.CodeGenerator) = gen(impl)
  private[akka] def gen(impl: akka.grpc.gen.CodeGenerator) = protocbridge.JvmGenerator(
    impl.name,
    new akka.grpc.sbt.AkkaGrpcPlugin.ProtocBridgeSbtPluginCodeGenerator(impl)
  )
}

import scalapb.compiler.GeneratorParams
import protocbridge.Artifact
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import akka.grpc.gen.scaladsl.Service
import akka.grpc.gen.scaladsl.ScalaCodeGenerator

object LagomScalaClientCodeGenerator extends ScalaCodeGenerator {
  override def name = "lagom-grpc-scaladsl-client"

  // This generator MUST be run next to ScalaClientCodeGenerator as
  // it assumes the code generated there will exist.
  override def perServiceContent = Set(generateStub)

  import templates.ScalaLagomClient.txt.LagomClient

  def generateStub(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(LagomClient(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}LagomClient.scala")
    b.build
  }

  override val suggestedDependencies =
    // TODO: remove grpc-stub dependency once we have a akka-http based client #193
    Artifact("io.grpc", "grpc-stub", scalapb.compiler.Version.grpcJavaVersion) +: super.suggestedDependencies

  private def parseParameters(params: String): GeneratorParams = {
    params.split(",").map(_.trim).filter(_.nonEmpty).foldLeft[GeneratorParams](GeneratorParams()) {
      case (p, "java_conversions")      => p.copy(javaConversions = true)
      case (p, "flat_package")          => p.copy(flatPackage = true)
      case (p, "grpc")                  => p.copy(grpc = true)
      case (p, "single_line_to_string") => p.copy(singleLineToProtoString = true)
      case (x, _)                       => x
    }
  }

}

