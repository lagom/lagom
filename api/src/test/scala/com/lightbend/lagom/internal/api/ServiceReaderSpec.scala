/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.lang.reflect.Type
import java.util
import java.util.Optional
import akka.util.ByteString
import com.fasterxml.jackson.annotation.{ JsonAutoDetect, JsonCreator }
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.lightbend.lagom.javadsl.api.Descriptor.RestCallId
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.api.paging.Page
import com.lightbend.lagom.javadsl.api.transport.{ MessageProtocol, Method }
import com.lightbend.lagom.api.mock.BlogService._
import com.lightbend.lagom.api.mock.{ BlogService, MockService }
import org.pcollections.{ HashTreePMap, TreePVector }
import org.scalatest._
import scala.collection.JavaConverters._
import akka.NotUsed
import com.lightbend.lagom.api.mock.ScalaMockService
import com.lightbend.lagom.api.mock.ScalaMockServiceWrong

class ServiceReaderSpec extends WordSpec with Matchers with Inside {

  "The service reader" should {
    "read a simple Java service descriptor" in {
      val descriptor = ServiceReader.readServiceDescriptor(getClass.getClassLoader, classOf[MockService])

      descriptor.calls().size() should ===(1)
      val endpoint = descriptor.calls().get(0)

      endpoint.callId() should ===(new RestCallId(Method.GET, "/hello/:name"))
      inside(endpoint.idSerializer) {
        case typeSerializer: UnresolvedTypeIdSerializer[_] =>
          typeSerializer.idType should ===(classOf[String])
      }
      inside(endpoint.requestSerializer) {
        case typeSerializer: UnresolvedMessageTypeSerializer[_] => typeSerializer.entityType should ===(classOf[NotUsed])
      }
      inside(endpoint.responseSerializer) {
        case typeSerializer: UnresolvedMessageTypeSerializer[_] => typeSerializer.entityType should ===(classOf[String])
      }
    }

    "read a simple Scala service descriptor" in {
      val descriptor = ServiceReader.readServiceDescriptor(getClass.getClassLoader, classOf[ScalaMockService])

      descriptor.calls().size() should ===(1)
      val endpoint = descriptor.calls().get(0)

      endpoint.callId() should ===(new RestCallId(Method.GET, "/hello/:name"))
      inside(endpoint.idSerializer) {
        case typeSerializer: UnresolvedTypeIdSerializer[_] =>
          typeSerializer.idType should ===(classOf[String])
      }
      inside(endpoint.requestSerializer) {
        case typeSerializer: UnresolvedMessageTypeSerializer[_] => typeSerializer.entityType should ===(classOf[NotUsed])
      }
      inside(endpoint.responseSerializer) {
        case typeSerializer: UnresolvedMessageTypeSerializer[_] => typeSerializer.entityType should ===(classOf[String])
      }
    }

    "fail to read a Scala service descriptor from a class" in {
      intercept[IllegalArgumentException] {
        ServiceReader.readServiceDescriptor(getClass.getClassLoader, classOf[ScalaMockServiceWrong])
      }
    }

    "resolve the service descriptor ids" in {
      val descriptor = blogServiceDescriptor
      val blogId = new BlogId("some name")
      val blogRawId = createRawId(List("blogId" -> "some name"), Nil)
      val pagedPosts = new PagedPosts(blogId, new Page(Optional.of(3), Optional.of(10)))
      val pagedPostsRawId = createRawId(List("blogId" -> "some name"), List("pageNo" -> Some("3"), "pageSize" -> Some("10")))
      val posts = new PagedPosts(blogId, new Page(Optional.empty(), Optional.empty()))
      val postsRawId = createRawId(List("blogId" -> "some name"), List("pageNo" -> None, "pageSize" -> None))
      val postId = new PostId(blogId, 10)
      val postRawId = createRawId(List("blogId" -> "some name", "postId" -> "10"), Nil)
      val commentId = new CommentId(postId, 20)
      val commentRawId = createRawId(List("blogId" -> "some name", "postId" -> "10", "commentId" -> "20"), Nil)

      val blogEndpoint = descriptor.calls().get(0)
      blogEndpoint.idSerializer().deserialize(blogRawId) should ===(blogId)
      blogEndpoint.idSerializer().asInstanceOf[IdSerializer[BlogId]].serialize(blogId) should ===(blogRawId)

      val postsEndpoint = descriptor.calls().get(1)
      postsEndpoint.idSerializer().deserialize(pagedPostsRawId) should ===(pagedPosts)
      postsEndpoint.idSerializer().asInstanceOf[IdSerializer[PagedPosts]].serialize(pagedPosts) should ===(pagedPostsRawId)
      postsEndpoint.idSerializer().deserialize(postsRawId) should ===(posts)
      postsEndpoint.idSerializer().asInstanceOf[IdSerializer[PagedPosts]].serialize(posts) should ===(postsRawId)

      val postEndpoint = descriptor.calls().get(2)
      postEndpoint.idSerializer().deserialize(postRawId) should ===(postId)
      postEndpoint.idSerializer().asInstanceOf[IdSerializer[PostId]].serialize(postId) should ===(postRawId)

      val commentEndpoint = descriptor.calls().get(3)
      commentEndpoint.idSerializer().deserialize(commentRawId) should ===(commentId)
      commentEndpoint.idSerializer().asInstanceOf[IdSerializer[CommentId]].serialize(commentId) should ===(commentRawId)
    }

    "resolve message serializers" in {
      val descriptor = blogServiceDescriptor
      val blog = new Blog("foo")
      val blogEndpoint = descriptor.calls().get(0)
      val serializer = blogEndpoint.responseSerializer().asInstanceOf[StrictMessageSerializer[Blog]]

      val serialized = serializer.serializerForRequest().serialize(blog)
      serialized.utf8String should include(blog.name())

      val deserializer = serializer.deserializer(new MessageProtocol())
      deserializer.deserialize(serialized) should ===(blog)
    }

  }

  def blogServiceDescriptor = ServiceReader.resolveServiceDescriptor(ServiceReader.readServiceDescriptor(
    getClass.getClassLoader,
    classOf[BlogService]
  ), getClass.getClassLoader, Map(JacksonPlaceholderSerializerFactory -> new SimpleJacksonSerializerFactory),
    Map(JacksonPlaceholderExceptionSerializer -> new SimpleJacksonExceptionSerializer))

  private class SimpleJacksonSerializerFactory extends SerializerFactory {
    private val objectMapper = new ObjectMapper()
    objectMapper.setVisibilityChecker(
      objectMapper.getSerializationConfig.getDefaultVisibilityChecker
        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
    )
    objectMapper.registerModule(new Jdk8Module)
      .registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
    override def messageSerializerFor[MessageEntity](`type`: Type): MessageSerializer[MessageEntity, _] = {
      val javaType = objectMapper.getTypeFactory.constructType(`type`)
      val reader = objectMapper.readerFor(javaType)
      val writer = objectMapper.writerFor(javaType)

      val serializer = new NegotiatedSerializer[MessageEntity, ByteString]() {
        override def serialize(messageEntity: MessageEntity): ByteString = {
          val builder = ByteString.newBuilder
          writer.writeValue(builder.asOutputStream, messageEntity)
          builder.result()
        }
        override def protocol() = new MessageProtocol(Optional.of("application/json"), Optional.of("utf-8"), Optional.empty())
      }

      val deser = new NegotiatedDeserializer[MessageEntity, ByteString] {
        override def deserialize(bytes: ByteString): MessageEntity =
          reader.readValue(bytes.iterator.asInputStream).asInstanceOf[MessageEntity]
      }

      new StrictMessageSerializer[MessageEntity] {
        override def deserializer(messageHeader: MessageProtocol) = deser
        override def serializerForResponse(acceptedMessageHeaders: util.List[MessageProtocol]) = serializer
        override def serializerForRequest() = serializer
      }
    }
  }

  private class SimpleJacksonExceptionSerializer extends ExceptionSerializer {
    override def serialize(exception: Throwable, accept: util.Collection[MessageProtocol]): RawExceptionMessage = ???
    override def deserialize(message: RawExceptionMessage): Throwable = ???
  }

  private def createRawId(pathParams: List[(String, String)], queryParams: List[(String, Option[String])]) = {
    RawId.of(
      TreePVector.from(pathParams.map { case (n, v) => RawId.PathParam.of(n, v) }.asJava),
      HashTreePMap.from(queryParams.map { case (n, v) => n -> v.fold(TreePVector.empty[String])(TreePVector.singleton) }.toMap.asJava)
    )
  }

}
