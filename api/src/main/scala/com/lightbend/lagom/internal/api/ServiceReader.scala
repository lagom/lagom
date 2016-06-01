/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.lang.invoke.MethodHandles
import java.lang.reflect.{ InvocationHandler, Method, ParameterizedType, Type, Proxy => JProxy }

import com.google.common.reflect.TypeToken
import com.lightbend.lagom.javadsl.api._
import com.lightbend.lagom.javadsl.api.Descriptor._
import com.lightbend.lagom.javadsl.api.deser._
import akka.NotUsed
import org.pcollections.TreePVector

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.control.NonFatal

/**
 * Reads a service interface
 */
object ServiceReader {

  final val DescriptorMethodName = "descriptor"

  /**
   * In order to invoke default methods of a proxy in Java 8 reflectively, we need to create a MethodHandles.Lookup
   * instance that is allowed to lookup private methods. The only way to do that is via reflection.
   */
  private val methodHandlesLookupConstructor = classOf[MethodHandles.Lookup].getDeclaredConstructor(classOf[Class[_]], classOf[Int])
  if (!methodHandlesLookupConstructor.isAccessible) {
    methodHandlesLookupConstructor.setAccessible(true)
  }

  def readServiceDescriptor(classLoader: ClassLoader, serviceInterface: Class[_ <: Service]): Descriptor = {
    val invocationHandler = new ServiceInvocationHandler(classLoader, serviceInterface)
    val serviceStub = JProxy.newProxyInstance(classLoader, Array(serviceInterface), invocationHandler).asInstanceOf[Service]
    serviceStub.descriptor()
  }

  def resolveServiceDescriptor(descriptor: Descriptor, classLoader: ClassLoader,
                               builtInSerializerFactories:  Map[PlaceholderSerializerFactory, SerializerFactory],
                               builtInExceptionSerializers: Map[PlaceholderExceptionSerializer, ExceptionSerializer]): Descriptor = {

    val builtInIdSerializers: Map[Type, PathParamSerializer[_]] = Map(
      classOf[String] -> PathParamSerializers.STRING,
      classOf[java.lang.Long] -> PathParamSerializers.LONG,
      classOf[java.lang.Integer] -> PathParamSerializers.INTEGER,
      classOf[java.lang.Boolean] -> PathParamSerializers.BOOLEAN,
      classOf[java.util.Optional[_]] -> PathParamSerializers.OPTIONAL
    )

    val builtInMessageSerializers: Map[Type, MessageSerializer[_, _]] = Map(
      classOf[NotUsed] -> MessageSerializers.NOT_USED,
      classOf[String] -> MessageSerializers.STRING
    )

    val serializerFactory = descriptor.serializerFactory() match {
      case placeholder: PlaceholderSerializerFactory =>
        builtInSerializerFactories.getOrElse(placeholder, {
          throw new IllegalArgumentException("PlaceholderSerializerFactory " + placeholder + " not found")
        })
      case other => other
    }

    val exceptionSerializer = descriptor.exceptionSerializer() match {
      case placeholder: PlaceholderExceptionSerializer =>
        builtInExceptionSerializers.getOrElse(placeholder, {
          throw new IllegalArgumentException("PlaceholderExceptionSerializer " + placeholder + " not found")
        })
      case other => other
    }

    val serviceResolver = new ServiceCallResolver(
      builtInIdSerializers ++ descriptor.pathParamSerializers().asScala,
      builtInMessageSerializers ++ descriptor.messageSerializers().asScala, serializerFactory
    )

    val endpoints = descriptor.calls().asScala.map { ep =>
      val endpoint = ep.asInstanceOf[Call[Any, Any]]

      val methodRefServiceCallHolder = ep.serviceCallHolder() match {
        case methodRef: MethodRefServiceCallHolder => methodRef
        case other                                 => throw new IllegalArgumentException("Unknown ServiceCallHolder type: " + other)
      }

      val method = methodRefServiceCallHolder.methodReference match {
        case preResolved: Method => preResolved
        case lambda =>
          try {
            MethodRefResolver.resolveMethodRef(lambda)
          } catch {
            case NonFatal(e) =>
              throw new IllegalStateException("Unable to resolve method for service call with ID " + ep.callId() +
                ". Ensure that the you have passed a method reference (ie, this::someMethod). Passing anything else, " +
                "for example lambdas, anonymous classes or actual implementation classes, is forbidden in declaring a " +
                "service descriptor.", e)
          }
      }

      val serviceCallType = TypeToken.of(method.getGenericReturnType)
        .asInstanceOf[TypeToken[ServiceCall[_, _]]]
        .getSupertype(classOf[ServiceCall[_, _]])
        .getType match {
          case param: ParameterizedType => param
          case _                        => throw new IllegalStateException("ServiceCall is not a parameterized type?")
        }

      // Now get the type arguments
      val (request, response) = serviceCallType.getActualTypeArguments match {
        case Array(request, response) =>
          if (method.getReturnType == classOf[ServiceCall[_, _]]) {
            (request, response)
          } else {
            throw new IllegalArgumentException("Service calls must return ServiceCall, subtypes are not allowed")
          }
        case _ => throw new IllegalStateException("ServiceCall does not have 2 type arguments?")
      }

      val serviceCallHolder = constructServiceCallHolder(serviceResolver, method)

      val endpointWithCallId = endpoint.callId() match {
        case named: NamedCallId if named.name() == "__unresolved__" => endpoint.withCallId(new NamedCallId(method.getName))
        case other => endpoint // todo validate paths against method arguments
      }

      val endpointWithCircuitBreaker = if (endpointWithCallId.circuitBreaker().isPresent) {
        endpointWithCallId
      } else {
        endpointWithCallId.withCircuitBreaker(descriptor.circuitBreaker())
      }

      endpointWithCircuitBreaker
        .withServiceCallHolder(serviceCallHolder)
        .withRequestSerializer(serviceResolver.resolveMessageSerializer(endpoint.requestSerializer(), request))
        .withResponseSerializer(serviceResolver.resolveMessageSerializer(endpoint.responseSerializer(), response))
    }

    val acls = descriptor.acls.asScala ++ endpoints.collect {
      case autoAclCall if autoAclCall.autoAcl.asScala.map(_.booleanValue).getOrElse(descriptor.autoAcl) =>
        val pathSpec = Path.fromCallId(autoAclCall.callId).regex.regex
        val method = autoAclCall.callId match {
          case named: NamedCallId => methodFromSerializer(autoAclCall.requestSerializer, autoAclCall.responseSerializer)
          case path: PathCallId   => methodFromSerializer(autoAclCall.requestSerializer, autoAclCall.responseSerializer)
          case rest: RestCallId   => rest.method
        }
        ServiceAcl.methodAndPath(method, pathSpec)
    }

    descriptor.replaceAllCalls(TreePVector.from(endpoints.asJava.asInstanceOf[java.util.List[Call[_, _]]]))
      .withExceptionSerializer(exceptionSerializer)
      .replaceAllAcls(TreePVector.from(acls.asJava))
  }

  private def constructServiceCallHolder[Request, Response](serviceCallResolver: ServiceCallResolver, method: Method): ServiceCallHolder = {
    val serializers = method.getGenericParameterTypes.toSeq.map { arg =>
      serviceCallResolver.resolvePathParamSerializer(new UnresolvedTypePathParamSerializer[AnyRef], arg)
    }

    import scala.collection.JavaConverters._

    val theMethod = method

    new MethodServiceCallHolder {
      override def invoke(arguments: Seq[AnyRef]): Seq[Seq[String]] = {
        if (arguments == null) {
          Nil
        } else {
          arguments.zip(serializers).map {
            case (arg, serializer) => serializer.serialize(arg).asScala
          }
        }
      }
      override def create(service: Any, parameters: Seq[Seq[String]]): ServiceCall[_, _] = {
        val args = parameters.zip(serializers).map {
          case (params, serializer) => serializer.deserialize(TreePVector.from(params.asJavaCollection))
        }

        theMethod.invoke(service, args: _*).asInstanceOf[ServiceCall[_, _]]
      }
      override val method: Method = theMethod
    }

  }

  class ServiceInvocationHandler(classLoader: ClassLoader, serviceInterface: Class[_ <: Service]) extends InvocationHandler {
    override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]): AnyRef = {
      // If it's a default method, invoke it
      if (method.isDefault) {
        // This is the way to invoke default methods via reflection, using the JDK7 method handles API
        val declaringClass = method.getDeclaringClass
        // We create a MethodHandles.Lookup that is allowed to look up private methods
        methodHandlesLookupConstructor.newInstance(declaringClass, new Integer(MethodHandles.Lookup.PRIVATE))
          // Now using unreflect special, we get the default method from the declaring class, rather than the proxy
          .unreflectSpecial(method, declaringClass)
          // We bind to the proxy so that we end up invoking on the proxy
          .bindTo(proxy)
          // And now we actually invoke it
          .invokeWithArguments(args: _*)

      } else if (method.getName == DescriptorMethodName && method.getParameterCount == 0) {
        if (ScalaSig.isScala(serviceInterface)) {
          if (serviceInterface.isInterface()) {
            val implClass = Class.forName(serviceInterface.getName + "$class", false, classLoader)
            val method = implClass.getMethod(DescriptorMethodName, serviceInterface)
            method.invoke(null, proxy.asInstanceOf[AnyRef])
          } else {
            throw new IllegalArgumentException("Service.descriptor must be implemented in a trait")
          }
        } else {
          // If this is the descriptor method, and it doesn't have a default implementation, throw an exception
          throw new IllegalArgumentException("Service.descriptor must be implemented as a default method")
        }
      } else if (classOf[ServiceCall[_, _]].isAssignableFrom(method.getReturnType)) {
        throw new IllegalStateException("Service call method " + method + " was invoked on self describing service " +
          serviceInterface + " while loading descriptor, which is not allowed.")
      } else {
        throw new IllegalStateException("Abstract method " + method + " invoked on self describing service " +
          serviceInterface + " while loading descriptor, which is not allowed.")
      }
    }
  }

  private def methodFromSerializer(requestSerializer: MessageSerializer[_, _], responseSerializer: MessageSerializer[_, _]) = {
    if (requestSerializer.isStreamed || responseSerializer.isStreamed) {
      com.lightbend.lagom.javadsl.api.transport.Method.GET
    } else if (requestSerializer.isUsed) {
      com.lightbend.lagom.javadsl.api.transport.Method.POST
    } else {
      com.lightbend.lagom.javadsl.api.transport.Method.GET
    }
  }
}

/**
 * Internal API.
 *
 * Service call holder that holds the original method reference that was passed to the descriptor when the service
 * descriptor was defined.
 *
 * @param methodReference The method reference (a lambda object).
 */
private[lagom] case class MethodRefServiceCallHolder(methodReference: Any) extends ServiceCallHolder

/**
 * Internal API.
 *
 * Service call holder that's used by the service router and client implementor to essentially the raw id to and from
 * invocations of the service call method.
 */
private[lagom] trait MethodServiceCallHolder extends ServiceCallHolder {
  val method: Method
  def create(service: Any, parameters: Seq[Seq[String]]): ServiceCall[_, _]
  def invoke(arguments: Seq[AnyRef]): Seq[Seq[String]]
}