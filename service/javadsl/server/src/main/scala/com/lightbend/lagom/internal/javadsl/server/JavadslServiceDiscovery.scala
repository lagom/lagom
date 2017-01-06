/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.server

import java.io.File
import java.lang.reflect.Type
import java.util

import com.google.inject.{ Binding, Module }
import com.google.inject.spi._
import com.lightbend.lagom.internal.javadsl.api._
import com.lightbend.lagom.internal.spi.{ ServiceAcl, ServiceDescription, ServiceDiscovery }
import com.lightbend.lagom.javadsl.api.{ Descriptor, Service }
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol
import org.apache.commons.lang3.ClassUtils
import play.api.inject.guice.{ BinderOption, GuiceableModule }
import play.api.{ Configuration, Environment, Logger, Mode }

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

class JavadslServiceDiscovery extends ServiceDiscovery {

  private val log = Logger(this.getClass)

  /**
   * These stub factories are necessary in order to resolve a service descriptor.
   */
  private final val serializerFactories: Map[PlaceholderSerializerFactory, SerializerFactory] =
    Map(JacksonPlaceholderSerializerFactory -> new SerializerFactoryStub)
  private final val exceptionSerializers: Map[PlaceholderExceptionSerializer, ExceptionSerializer] =
    Map(JacksonPlaceholderExceptionSerializer -> new ExceptionSerializerStub)

  override def discoverServices(classLoader: ClassLoader): util.List[ServiceDescription] = {
    val modules = loadModules(classLoader)
    val serviceDescriptions = resolveServiceInterfaces(modules).flatMap { serviceInterface =>
      val unresolvedDescriptor = ServiceReader.readServiceDescriptor(classLoader, serviceInterface)
      val resolvedDescriptor = ServiceReader.resolveServiceDescriptor(unresolvedDescriptor, classLoader, serializerFactories, exceptionSerializers)
      if (resolvedDescriptor.locatableService)
        Some(createServiceDescription(resolvedDescriptor))
      else
        None
    }
    serviceDescriptions.asJava
  }

  private def createServiceDescription(descriptor: Descriptor): ServiceDescription = {
    val convertedAcls = descriptor.acls().asScala.map { acl =>
      new ServiceAcl {
        override def method() = acl.method.asScala.map(_.name).asJava
        override def pathPattern() = acl.pathRegex()
      }.asInstanceOf[ServiceAcl]
    }.asJava

    new ServiceDescription {
      override def acls() = convertedAcls
      override def name() = descriptor.name
    }
  }

  /**
   * Load Guice modules for a given class loader
   */
  private def loadModules(classLoader: ClassLoader): Seq[Module] = {
    val env = Environment(new File("."), classLoader, Mode.Test)
    val conf = Configuration.load(env)
    val modules = GuiceableModule.loadModules(env, conf)
    val binderOptions = BinderOption.defaults
    GuiceableModule.guiced(env, conf, binderOptions)(modules)
  }

  /**
   * Scans the given Guice modules to find and return the service interfaces.
   */
  private def resolveServiceInterfaces(modules: Seq[Module]): Seq[Class[_ <: Service]] = {
    val serviceInterfaces = mutable.ListBuffer.empty[Class[_ <: Service]]

    // Iterates through all bindings of a module to find and add the service interfaces to the `serviceInterfaces` buffer
    def addServiceInterfaces(element: Element): Unit =
      // Starting point is to create a `DefaultElementVisitor` to iterate through all the binding of a module
      element.acceptVisitor(new DefaultElementVisitor[Element] {
        override def visit[T](binding: Binding[T]): Element = {
          // Now we are only interested in the `UntargettedBinding`s to
          // filter out the binding of [[com.lightbend.lagom.javadsl.api.Service]] itself
          binding.acceptTargetVisitor(new DefaultBindingTargetVisitor[Any, Any] {
            override def visit(untargettedBinding: UntargettedBinding[_]): AnyRef = {
              // For each untargetted binding we check if the interface extends from [[com.lightbend.lagom.javadsl.api.Service]]
              // If so we add this interface to the `serviceInterfaces` buffer
              val serviceInterfaceOpt = serviceInterfaceResolver(untargettedBinding.getKey.getTypeLiteral.getRawType)
              serviceInterfaces ++= serviceInterfaceOpt
              untargettedBinding
            }
          })
          binding
        }
      })

    // Iterate through the Guice modules and find bindings which interface has `Service` as a superclass.
    // Add these service interfaces to the `serviceInterfaces` buffer and return the buffer
    Elements.getElements(modules.asJava).asScala.foreach(addServiceInterfaces)
    serviceInterfaces
  }

  private final val ExpectedDescriptorParamCount = 0
  private final val ExpectedDescriptorReturnType = classOf[Descriptor]

  // Checks if the given class has an interface that extends from [[com.lightbend.lagom.javadsl.api.Service]]
  private[lagom] def serviceInterfaceResolver(clazz: Class[_]): Option[Class[_ <: Service]] = {
    // Checks if a the given service interface has implemented the method `descriptor()`
    def verify(serviceInterface: Class[_]): Either[String, Class[_ <: Service]] =
      try {
        val method = serviceInterface.getDeclaredMethod(ServiceReader.DescriptorMethodName)
        if (method.getParameterCount == ExpectedDescriptorParamCount && method.getReturnType == ExpectedDescriptorReturnType)
          Right(serviceInterface.asSubclass(classOf[Service]))
        else
          Left(s"Service interface $serviceInterface does contain expected method ${ServiceReader.DescriptorMethodName} with an invalid return type or parameter count. " +
            s"Expected return type: $ExpectedDescriptorReturnType, actual: ${method.getReturnType}, Expected parameter count: $ExpectedDescriptorParamCount, actual: ${method.getParameterCount}")
      } catch {
        case _: NoSuchMethodException =>
          Left(s"Service interface $serviceInterface does not contain expected method ${ServiceReader.DescriptorMethodName}().")
      }

    // Get interfaces of service implementation, e.g. HelloServiceImpl > HelloService
    val interfaces = ClassUtils.getAllInterfaces(clazz)

    // Collect the first interface, starting from the lowest interface in the inheritance hierarchy, that
    // is assignable from [[com.lightbend.lagom.javadsl.api.Service]] and that overrides the `descriptor` method.
    val errorsOrServiceInterfaces = interfaces.asScala.collect {
      case serviceInterface if classOf[Service].isAssignableFrom(serviceInterface) => verify(serviceInterface)
    }

    val serviceInterfaceOpt = errorsOrServiceInterfaces.find(_.isRight).map(_.right.get)

    // Log errors if no service interface has implemented the `descriptor()` method
    // This can happen if the method name or signature has been changed.
    if (serviceInterfaceOpt.isEmpty)
      errorsOrServiceInterfaces.filter(_.isLeft).foreach(error => log.error(error.left.get))

    serviceInterfaceOpt
  }

  /**
   * This serializer factory stub is necessary in order to resolve a service descriptor.
   * The implementation of this stub is not relevant here because the api tools library is not interested
   * in the request or response body.
   */
  private class SerializerFactoryStub extends SerializerFactory {
    private def stub = throw new NotImplementedError("SerializerFactory is not provided in the api tools library.")
    override def messageSerializerFor[MessageEntity](`type`: Type): MessageSerializer[MessageEntity, _] =
      new StrictMessageSerializer[MessageEntity] {
        override def deserializer(messageHeader: MessageProtocol) = stub
        override def serializerForResponse(acceptedMessageHeaders: util.List[MessageProtocol]) = stub
        override def serializerForRequest() = stub
      }
  }

  /**
   * This exception serializer factory stub is necessary in order to resolve a service descriptor.
   * The implementation of this stub is not relevant here because the api tools library is not interested
   * in the request or response body.
   */
  private class ExceptionSerializerStub extends ExceptionSerializer {
    private def stub = throw new NotImplementedError("ExceptionSerializer is not provided in the api tools library.")
    override def serialize(exception: Throwable, accept: util.Collection[MessageProtocol]): RawExceptionMessage = stub
    override def deserialize(message: RawExceptionMessage): Throwable = stub
  }

}
