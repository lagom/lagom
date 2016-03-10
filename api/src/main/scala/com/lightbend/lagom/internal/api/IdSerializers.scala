/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.lang.reflect.Type
import java.util.Optional

import com.lightbend.lagom.javadsl.api.deser.{ IdSerializer, RawId }
import net.jodah.typetools.TypeResolver

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

trait UnresolvedIdSerializer[Id] extends IdSerializer[Id] {
  def serialize(id: Id) = throw new NotImplementedError("Cannot use unresolved id serializer to serialize ids")

  def deserialize(rawId: RawId) = throw new NotImplementedError("Cannot use unresolved id serializer to deserialize ids")

  def resolve(resolver: ServiceCallResolver, typeInfo: Option[Type]): IdSerializer[Id]
}

class UnresolvedTypeIdSerializer[Id](val idType: Type) extends UnresolvedIdSerializer[Id] {
  override def resolve(resolver: ServiceCallResolver, typeInfo: Option[Type]): IdSerializer[Id] = {
    resolver.idSerializerFor(idType)
  }
}

class UnresolvedFromFunctionIdSerializer[Id](name: String, paramTypes: Seq[Type], deserializeF: Seq[Any] => Id,
                                             serializeF: Id => Seq[Any]) extends UnresolvedIdSerializer[Id] {
  override def resolve(resolver: ServiceCallResolver, typeInfo: Option[Type]): IdSerializer[Id] = {
    new CompositeFromFunctionIdSerializer(name, paramTypes.map(resolver.idSerializerFor), deserializeF, serializeF)
  }
}

class CompositeFromFunctionIdSerializer[Id](name: String, paramSerializers: Seq[IdSerializer[_]], deserializeF: Seq[Any] => Id,
                                            serializeF: Id => Seq[Any]) extends IdSerializer[Id] {

  // This not only calculates the total parameters being extracted, it also validates that each composed id serializer
  // defines a hint for the number of parameters extracted.
  private val totalParams = paramSerializers.foldLeft(0) { (total, serializer) =>
    if (!serializer.numPathParamsHint.isPresent) {
      throw new IllegalArgumentException(s"Cannot compose IdSerializer that does not define a numPathParamsHint: $serializer. IdSerializer composition needs to know how many path parameters each serializer extracts, so that those path parameters can be removed from the ID when the next composed serializer is applied. To ensure that this IdSerializer can be composed, make sure to override the numPathParmsHint method to return the number of path parameters that the serializer will extract. The IdSerializer that is attempting to compose this is $name.")
    } else {
      total + serializer.numPathParamsHint.get
    }
  }

  override def numPathParamsHint(): Optional[Integer] = Optional.of(totalParams)

  override def serialize(id: Id) = {
    paramSerializers.zip(serializeF(id)).foldLeft(RawId.EMPTY) {
      case (rawId, (serializer, value)) =>
        val rawIdToAdd = serializer.asInstanceOf[IdSerializer[Any]].serialize(value)
        RawId.of(rawId.pathParams.plusAll(rawIdToAdd.pathParams), rawId.queryParams.plusAll(rawIdToAdd.queryParams))
    }
  }

  override def deserialize(rawId: RawId) = {
    val (deserializedParams, _) = paramSerializers.foldLeft((Vector.empty[Any], rawId)) {
      case ((deserialized, remainingId), serializer) =>
        val toRemove = serializer.numPathParamsHint.get
        val value = serializer.deserialize(remainingId)
        val newRemaingId = RawId.of(rawId.pathParams.subList(toRemove, rawId.pathParams.size), rawId.queryParams)
        (deserialized :+ value, newRemaingId)
    }
    deserializeF(deserializedParams)
  }

  override def toString = s"IdSerializer($name)"
}

object InternalIdSerializers {

  private def resolveFunctionArguments[F: ClassTag](f: F): Seq[Type] = {
    TypeResolver.resolveRawArguments(implicitly[ClassTag[F]].runtimeClass.asInstanceOf[Class[F]], f.getClass).dropRight(1)
  }

  def fromFunction[Id, Arg1](name: String, deserialize: java.util.function.Function[Arg1, Id],
                             serialize: java.util.function.Function[Id, Arg1]): IdSerializer[Id] = {

    val types = resolveFunctionArguments[java.util.function.Function[_, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked) => deserialize.apply(arg1)
    }, id => Seq(serialize.apply(id)))
  }

  def fromBiFunction[Id, Arg1, Arg2](name: String, deserialize: java.util.function.BiFunction[Arg1, Arg2, Id],
                                     serialize: java.util.function.Function[Id, java.util.List[Any]]): IdSerializer[Id] = {

    val types = resolveFunctionArguments[java.util.function.BiFunction[_, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked) => deserialize.apply(arg1, arg2)
    }, id => serialize.apply(id).asScala)
  }

  /**
   * Copy this to the reply to generate the IdSerializers.create methods
   */
  private def generateApi(): Unit = {
    (3 to 22).foreach { count =>
      val args = 1 to count
      val argTypeList = args.map("Arg" + _).mkString(", ")
      println(
        s"""|    public static <Id, $argTypeList> IdSerializer<Id> create(String name,
            |            Function$count<$argTypeList, Id> deserialize,
            |            Function<Id, List<Object>> serialize) {
            |        return InternalIdSerializers.fromFunction$count(name, deserialize, serialize);
            |    }
       """.stripMargin
      )
    }
  }

  /**
   * Copy this to the repl to generate the functions below
   */
  private def generateInternal(): Unit = {
    (3 to 22).foreach { count =>
      val args = 1 to count
      val argTypeList = args.map("Arg" + _).mkString(", ")
      val argList = args.map("arg" + _).mkString(", ")
      val argMatch = args.map(a => s"arg$a: Arg$a @unchecked").mkString(", ")
      val argWildcards = args.map(_ => "_").mkString(",")
      println(
        s"""|  def fromFunction$count[Id, $argTypeList](name: String,
            |    deserialize: akka.japi.function.Function$count[$argTypeList, Id],
            |    serialize: java.util.function.Function[Id, java.util.List[Any]]): IdSerializer[Id] = {
            |
            |    val types = resolveFunctionArguments[akka.japi.function.Function$count[$argWildcards, _]](deserialize)
            |
            |    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
            |      case Seq($argMatch) => deserialize.apply($argList)
            |    }, id => serialize.apply(id).asScala)
            |  }
       """.stripMargin
      )
    }
  }

  def fromFunction3[Id, Arg1, Arg2, Arg3](
    name:        String,
    deserialize: akka.japi.function.Function3[Arg1, Arg2, Arg3, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function3[_, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked) => deserialize.apply(arg1, arg2, arg3)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction4[Id, Arg1, Arg2, Arg3, Arg4](
    name:        String,
    deserialize: akka.japi.function.Function4[Arg1, Arg2, Arg3, Arg4, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function4[_, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction5[Id, Arg1, Arg2, Arg3, Arg4, Arg5](
    name:        String,
    deserialize: akka.japi.function.Function5[Arg1, Arg2, Arg3, Arg4, Arg5, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function5[_, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction6[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6](
    name:        String,
    deserialize: akka.japi.function.Function6[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function6[_, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction7[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7](
    name:        String,
    deserialize: akka.japi.function.Function7[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function7[_, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction8[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8](
    name:        String,
    deserialize: akka.japi.function.Function8[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function8[_, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction9[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9](
    name:        String,
    deserialize: akka.japi.function.Function9[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function9[_, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction10[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10](
    name:        String,
    deserialize: akka.japi.function.Function10[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function10[_, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction11[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11](
    name:        String,
    deserialize: akka.japi.function.Function11[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function11[_, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction12[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12](
    name:        String,
    deserialize: akka.japi.function.Function12[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function12[_, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction13[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13](
    name:        String,
    deserialize: akka.japi.function.Function13[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function13[_, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction14[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14](
    name:        String,
    deserialize: akka.japi.function.Function14[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function14[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction15[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15](
    name:        String,
    deserialize: akka.japi.function.Function15[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function15[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction16[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16](
    name:        String,
    deserialize: akka.japi.function.Function16[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function16[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked, arg16: Arg16 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction17[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17](
    name:        String,
    deserialize: akka.japi.function.Function17[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function17[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked, arg16: Arg16 @unchecked, arg17: Arg17 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction18[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18](
    name:        String,
    deserialize: akka.japi.function.Function18[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function18[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked, arg16: Arg16 @unchecked, arg17: Arg17 @unchecked, arg18: Arg18 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction19[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19](
    name:        String,
    deserialize: akka.japi.function.Function19[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function19[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked, arg16: Arg16 @unchecked, arg17: Arg17 @unchecked, arg18: Arg18 @unchecked, arg19: Arg19 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction20[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20](
    name:        String,
    deserialize: akka.japi.function.Function20[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function20[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked, arg16: Arg16 @unchecked, arg17: Arg17 @unchecked, arg18: Arg18 @unchecked, arg19: Arg19 @unchecked, arg20: Arg20 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction21[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21](
    name:        String,
    deserialize: akka.japi.function.Function21[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function21[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked, arg16: Arg16 @unchecked, arg17: Arg17 @unchecked, arg18: Arg18 @unchecked, arg19: Arg19 @unchecked, arg20: Arg20 @unchecked, arg21: Arg21 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21)
    }, id => serialize.apply(id).asScala)
  }

  def fromFunction22[Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21, Arg22](
    name:        String,
    deserialize: akka.japi.function.Function22[Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21, Arg22, Id],
    serialize:   java.util.function.Function[Id, java.util.List[Any]]
  ): IdSerializer[Id] = {

    val types = resolveFunctionArguments[akka.japi.function.Function22[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]](deserialize)

    new UnresolvedFromFunctionIdSerializer[Id](name, types, {
      case Seq(arg1: Arg1 @unchecked, arg2: Arg2 @unchecked, arg3: Arg3 @unchecked, arg4: Arg4 @unchecked, arg5: Arg5 @unchecked, arg6: Arg6 @unchecked, arg7: Arg7 @unchecked, arg8: Arg8 @unchecked, arg9: Arg9 @unchecked, arg10: Arg10 @unchecked, arg11: Arg11 @unchecked, arg12: Arg12 @unchecked, arg13: Arg13 @unchecked, arg14: Arg14 @unchecked, arg15: Arg15 @unchecked, arg16: Arg16 @unchecked, arg17: Arg17 @unchecked, arg18: Arg18 @unchecked, arg19: Arg19 @unchecked, arg20: Arg20 @unchecked, arg21: Arg21 @unchecked, arg22: Arg22 @unchecked) => deserialize.apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22)
    }, id => serialize.apply(id).asScala)
  }
}
