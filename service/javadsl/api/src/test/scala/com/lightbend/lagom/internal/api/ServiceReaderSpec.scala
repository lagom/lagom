/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.lang.reflect.{ InvocationHandler, Type }
import java.util
import java.util.{ Optional, UUID }
import java.util.concurrent.CompletionStage

import akka.util.ByteString
import com.lightbend.lagom.javadsl.api.Descriptor.RestCallId
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.api.transport.{ MessageProtocol, Method }
import com.lightbend.lagom.api.mock._
import org.scalatest._
import com.lightbend.lagom.internal.javadsl.api.{ JacksonPlaceholderExceptionSerializer, JacksonPlaceholderSerializerFactory, MethodServiceCallHolder, ServiceReader }
import com.lightbend.lagom.javadsl.api._

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

class ServiceReaderSpec extends WordSpec with Matchers with Inside {

  "The service reader" should {
    "read a simple Java service descriptor" in {
      val descriptor = serviceDescriptor[MockService]

      descriptor.calls().size() should ===(1)
      val endpoint = descriptor.calls().get(0)

      endpoint.callId() should ===(new RestCallId(Method.GET, "/hello/:name"))
      inside(endpoint.requestSerializer) {
        case simple: SimpleSerializer[_] => simple.`type` should ===(classOf[UUID])
      }
      endpoint.responseSerializer should ===(MessageSerializers.STRING)
    }

    "fail to read a Java service descriptor from a public interface because the path parameter could not be serialized" in {
      intercept[IllegalPathParameterException] {
        val descriptor = serviceDescriptor[InvalidPathParameterService]
      }
    }

    "read a simple Scala service descriptor" in {
      val descriptor = serviceDescriptor[ScalaMockService]

      descriptor.calls().size() should ===(1)
      val endpoint = descriptor.calls().get(0)

      endpoint.callId() should ===(new RestCallId(Method.GET, "/hello/:name"))
      inside(endpoint.requestSerializer) {
        case simple: SimpleSerializer[_] => simple.`type` should ===(classOf[UUID])
      }
      endpoint.responseSerializer should ===(MessageSerializers.STRING)
    }

    "fail to read a Scala service descriptor from a class" in {
      intercept[IllegalArgumentException] {
        ServiceReader.readServiceDescriptor(getClass.getClassLoader, classOf[ScalaMockServiceWrong])
      }
    }

    "fail to read a Java service descriptor from a non-public interface (and report it in a user-friendly manner)" in {
      val caught = intercept[IllegalArgumentException] {
        ServiceReader.readServiceDescriptor(getClass.getClassLoader, classOf[NotPublicInterfaceService])
      }
      caught.getMessage should ===("Service API must be described in a public interface.")
    }

    "resolve the service descriptor path param serializers" in {
      val descriptor = serviceDescriptor[BlogService]

      def serializeArgs(call: Descriptor.Call[_, _], args: Seq[Any]): Seq[Seq[String]] = {
        call.serviceCallHolder() match {
          case method: MethodServiceCallHolder => method.invoke(args.asInstanceOf[Seq[AnyRef]])
        }
      }

      def deserializeParams(call: Descriptor.Call[_, _], params: Seq[Seq[String]]): Seq[Any] = {
        call.serviceCallHolder() match {
          case method: MethodServiceCallHolder =>
            class ArgsCapturingServiceCall(val args: Array[AnyRef]) extends ServiceCall[Any, Any] {
              override def invoke(request: Any): CompletionStage[Any] = ???
            }

            val blogService = java.lang.reflect.Proxy.newProxyInstance(classOf[BlogService].getClassLoader, Array(classOf[BlogService]), new InvocationHandler {
              override def invoke(proxy: scala.Any, method: java.lang.reflect.Method, args: Array[AnyRef]): AnyRef = {
                new ArgsCapturingServiceCall(args)
              }
            })

            method.create(blogService, params).asInstanceOf[ArgsCapturingServiceCall].args
        }

      }

      val blogCall = descriptor.calls().get(0)
      deserializeParams(blogCall, Seq(Seq("some name"))) should ===(Seq("some name"))
      serializeArgs(blogCall, Seq("some name")) should ===(Seq(Seq("some name")))

      val postsCall = descriptor.calls().get(1)
      deserializeParams(postsCall, Seq(Seq("some name"), Seq("3"), Seq("10"))) should ===(Seq("some name", Optional.of(3), Optional.of(10)))
      serializeArgs(postsCall, Seq("some name", Optional.of(3), Optional.of(10))) should ===(Seq(Seq("some name"), Seq("3"), Seq("10")))
      deserializeParams(postsCall, Seq(Seq("some name"), Seq(), Seq())) should ===(Seq("some name", Optional.empty, Optional.empty))
      serializeArgs(postsCall, Seq("some name", Optional.empty, Optional.empty)) should ===(Seq(Seq("some name"), Seq(), Seq()))

      val postCall = descriptor.calls().get(2)
      deserializeParams(postCall, Seq(Seq("some name"), Seq("10"))) should ===(Seq("some name", 10L))
      serializeArgs(postCall, Seq("some name", 10L)) should ===(Seq(Seq("some name"), Seq("10")))

      val commentCall = descriptor.calls().get(3)
      deserializeParams(commentCall, Seq(Seq("some name"), Seq("10"), Seq("20"))) should ===(Seq("some name", 10L, 20L))
      serializeArgs(commentCall, Seq("some name", 10L, 20L)) should ===(Seq(Seq("some name"), Seq("10"), Seq("20")))

      val commentRepeatColCall = descriptor.calls().get(4)
      deserializeParams(commentRepeatColCall, Seq(Seq("some name"), Seq("10", "20", "30"))) should ===(Seq("some name", util.Arrays.asList("10", "20", "30")))
      serializeArgs(commentRepeatColCall, Seq("some name", util.Arrays.asList("10", "20", "30"))) should ===(Seq(Seq("some name"), Seq("10", "20", "30")))

      val commentRepeatListCall = descriptor.calls().get(5)
      deserializeParams(commentRepeatListCall, Seq(Seq("some name"), Seq("10", "20", "30"))) should ===(Seq("some name", util.Arrays.asList("10", "20", "30")))
      serializeArgs(commentRepeatListCall, Seq("some name", util.Arrays.asList("10", "20", "30"))) should ===(Seq(Seq("some name"), Seq("10", "20", "30")))

      val commentRepeatSetCall = descriptor.calls().get(6)
      // test for Set is trickier must first sort elements
      val deser = deserializeParams(commentRepeatSetCall, Seq(Seq("some name"), Seq("10", "20", "30")))

      deser.head === "some name"
      deser(1) match {
        case set: util.HashSet[_] =>
          set.asInstanceOf[util.HashSet[String]].asScala.toSeq.sorted === Seq("10", "20", "30")
      }

      val ser = serializeArgs(commentRepeatSetCall, Seq("some name", new util.HashSet(util.Arrays.asList("10", "20", "30"))))
      ser.head === Seq("some name")
      ser(1) match {
        case seq => seq.sorted === Seq("10", "20", "30")
      }

      val uuidCall = descriptor.calls().get(7)
      val uuid = UUID.randomUUID()
      deserializeParams(uuidCall, Seq(Seq(uuid.toString))) should ===(Seq(uuid))
      serializeArgs(uuidCall, Seq(uuid)) should ===(Seq(Seq(uuid.toString)))

    }

    "fail to read a Java service descriptor from a public interface because a message type uses type variables" in {
      val exception = intercept[IllegalMessageTypeException] {
        serviceDescriptor[InvalidMessageTypeService]
      }
      exception.getMessage should include("<Foo>")
    }

    "fail to read a Java service descriptor from a public interface because a the topic type is missing" in {
      val exception = intercept[IllegalMessageTypeException] {
        serviceDescriptor[MissingTopicTypeService]
      }

      // When users don't set a Topic type, <Message> is the type of the topic:
      //     Topic  myMethod();
      // because <Message> is the unbound type used in Service#topic(). See com.lightbend.lagom.javadsl.api.Service
      exception.getMessage should include("<TopicMessageType>")
    }

  }

  def serviceDescriptor[S <: Service](implicit ct: ClassTag[S]) = {
    ServiceReader.resolveServiceDescriptor(ServiceReader.readServiceDescriptor(
      getClass.getClassLoader,
      ct.runtimeClass.asInstanceOf[Class[Service]]
    ), getClass.getClassLoader, Map(JacksonPlaceholderSerializerFactory -> new SimpleSerializerFactory),
      Map(JacksonPlaceholderExceptionSerializer -> new SimpleExceptionSerializer))
  }

  private class SimpleSerializerFactory extends SerializerFactory {
    override def messageSerializerFor[MessageEntity](`type`: Type): MessageSerializer[MessageEntity, _] = {
      new SimpleSerializer[MessageEntity](`type`)
    }
  }

  private class SimpleSerializer[MessageEntity](val `type`: Type) extends StrictMessageSerializer[MessageEntity] {
    val serializer = new NegotiatedSerializer[MessageEntity, ByteString]() {
      override def serialize(messageEntity: MessageEntity): ByteString = {
        ByteString.fromString(messageEntity.toString)
      }

      override def protocol() = new MessageProtocol(Optional.of("text/plain"), Optional.of("utf-8"), Optional.empty())
    }

    val deser = new NegotiatedDeserializer[MessageEntity, ByteString] {
      override def deserialize(bytes: ByteString): MessageEntity = bytes.utf8String.asInstanceOf[MessageEntity]
    }

    override def deserializer(messageHeader: MessageProtocol) = deser

    override def serializerForResponse(acceptedMessageHeaders: util.List[MessageProtocol]) = serializer

    override def serializerForRequest() = serializer
  }

  private class SimpleExceptionSerializer extends ExceptionSerializer {
    override def serialize(exception: Throwable, accept: util.Collection[MessageProtocol]): RawExceptionMessage = ???

    override def deserialize(message: RawExceptionMessage): Throwable = ???
  }

}
