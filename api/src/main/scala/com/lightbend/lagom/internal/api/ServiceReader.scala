/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.lang.invoke.MethodHandles
import java.lang.reflect.{ InvocationHandler, Method, ParameterizedType, Proxy => JProxy, Type }
import java.util.Optional

import com.google.common.reflect.TypeToken
import com.lightbend.lagom.javadsl.api._
import com.lightbend.lagom.javadsl.api.Descriptor.{ RestCallId, PathCallId, NamedCallId, Call }
import com.lightbend.lagom.javadsl.api.Service.SelfDescribingServiceCall
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.api.paging.Page
import akka.NotUsed
import org.pcollections.TreePVector

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

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
    val invocationHandler = new ServiceInvocationHandler(serviceInterface)
    val serviceStub = JProxy.newProxyInstance(classLoader, Array(serviceInterface), invocationHandler).asInstanceOf[Service]
    serviceStub.descriptor()
  }

  def resolveServiceDescriptor(descriptor: Descriptor, classLoader: ClassLoader,
                               builtInSerializerFactories:  Map[PlaceholderSerializerFactory, SerializerFactory],
                               builtInExceptionSerializers: Map[PlaceholderExceptionSerializer, ExceptionSerializer]): Descriptor = {

    val builtInIdSerializers: Map[Type, IdSerializer[_]] = Map(
      classOf[String] -> IdSerializers.STRING,
      classOf[java.lang.Long] -> IdSerializers.LONG,
      classOf[java.lang.Integer] -> IdSerializers.INTEGER,
      classOf[NotUsed] -> IdSerializers.NOT_USED,
      classOf[Page] -> IdSerializers.PAGE
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
      builtInIdSerializers ++ descriptor.idSerializers().asScala,
      builtInMessageSerializers ++ descriptor.messageSerializers().asScala, serializerFactory
    )

    val endpoints = descriptor.calls().asScala.map { ep =>
      val endpoint = ep.asInstanceOf[Call[Any, Any, Any]]
      endpoint
        .`with`(serviceResolver.resolveIdSerializer(endpoint.idSerializer()))
        .withRequestSerializer(serviceResolver.resolveMessageSerializer(endpoint.requestSerializer()))
        .withResponseSerializer(serviceResolver.resolveMessageSerializer(endpoint.responseSerializer()))
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

    descriptor.replaceAllCalls(TreePVector.from(endpoints.asJava.asInstanceOf[java.util.List[Call[_, _, _]]]))
      .`with`(exceptionSerializer)
      .replaceAllAcls(TreePVector.from(acls.asJava))
  }

  class ServiceInvocationHandler(serviceInterface: Class[_ <: Service]) extends InvocationHandler {
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

        // If this is the descriptor method, and it doesn't have a default implementation, throw an exception
      } else if (method.equals(classOf[Service].getDeclaredMethod(DescriptorMethodName))) {
        throw new IllegalArgumentException("Service.descriptor must be implemented as a default method")
      } else if (method.getParameterCount == 0 && classOf[ServiceCall[_, _, _]].isAssignableFrom(method.getReturnType)) {

        // Using Guava TypeToken, we find the parameterized IdServiceCall type of the return type of the method
        val idServiceCallType = TypeToken.of(method.getGenericReturnType)
          .asInstanceOf[TypeToken[ServiceCall[_, _, _]]]
          .getSupertype(classOf[ServiceCall[_, _, _]])
          .getType match {
            case param: ParameterizedType => param
            case _                        => throw new IllegalStateException("ServiceCall is not a parameterized type?")
          }

        // Now get the type arguments and construct a SelfDescribingServiceCall from it
        idServiceCallType.getActualTypeArguments match {
          case Array(id, request, response) =>
            if (method.getReturnType == classOf[ServiceCall[_, _, _]]) {
              SelfDescribingServiceCallStub[AnyRef, AnyRef, AnyRef](method, id, request, response)
            } else {
              throw new IllegalArgumentException("Service calls must return ServiceCall, subtypes are not allowed")
            }
          case _ => throw new IllegalStateException("ServiceCall does not have 3 type arguments?")
        }
      } else {
        throw new IllegalStateException("Abstract method " + method + " invoked on self describing service " +
          serviceInterface + " while loading descriptor, but it was not a service call.")
      }
    }
  }

  def methodFromSerializer(requestSerializer: MessageSerializer[_, _], responseSerializer: MessageSerializer[_, _]) = {
    if (requestSerializer.isStreamed || responseSerializer.isStreamed) {
      com.lightbend.lagom.javadsl.api.transport.Method.GET
    } else if (requestSerializer.isUsed) {
      com.lightbend.lagom.javadsl.api.transport.Method.POST
    } else {
      com.lightbend.lagom.javadsl.api.transport.Method.GET
    }
  }
}

case class SelfDescribingServiceCallStub[Id, Request, Response](method: Method, idType: Type, requestType: Type, responseType: Type) extends SelfDescribingServiceCall[Id, Request, Response] {
  def invoke(id: Id, request: Request) = throw new NotImplementedError("This service call is a stub that should not be invoked")
  def methodName = method.getName
}
