/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

//#registry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

object MyRegistry extends JsonSerializerRegistry {
  override val serializers = Vector(
    JsonSerializer[AddComment],
    JsonSerializer[AddPost]
  )
}
//#registry

//#application-cake
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents

abstract class MyApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with ClusterComponents {

  override lazy val jsonSerializerRegistry = MyRegistry
}
//#application-cake

object CreateActorSystem {

  //#create-actor-system
  import akka.actor.ActorSystem
  import akka.actor.setup.ActorSystemSetup
  import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

  val system = ActorSystem(
    "my-actor-system",
    ActorSystemSetup(
      JsonSerializerRegistry.serializationSetupFor(MyRegistry)
    )
  )
  //#create-actor-system

}
