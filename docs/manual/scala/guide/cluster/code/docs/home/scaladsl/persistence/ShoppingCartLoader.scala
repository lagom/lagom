/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.Method

import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import com.softwaremill.macwire._

import play.api.libs.json.Format
import play.api.libs.json.Json

import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import akka.NotUsed
import akka.util.Timeout
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import ShoppingCartExamples._

// #shopping-cart-loader
class ShoppingCartLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new ShoppingCartApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ShoppingCartApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[ShoppingCartService])
}

trait ShoppingCartComponents
    extends LagomServerComponents
    with SlickPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {
  implicit def executionContext: ExecutionContext

  override lazy val lagomServer: LagomServer                       = serverFor[ShoppingCartService](wire[ShoppingCartServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = ShoppingCartSerializerRegistry

  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(ShoppingCart.typeKey) { entityContext =>
      ShoppingCart(entityContext)
    }
  )
}

abstract class ShoppingCartApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with ShoppingCartComponents
    with LagomKafkaComponents {}
// #shopping-cart-loader

trait ShoppingCartService extends Service {
  def get(id: String): ServiceCall[NotUsed, ShoppingCartView]

  final override def descriptor: Descriptor = {
    import Service._
    named("shopping-cart")
      .withCalls(
        restCall(Method.GET, "/shoppingcart/:id", get _)
      )
  }
}

// #shopping-cart-service-impl
class ShoppingCartServiceImpl(
    clusterSharding: ClusterSharding,
    persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
    extends ShoppingCartService // class body follows
// #shopping-cart-service-impl
    {
  // #shopping-cart-entity-ref
  def entityRef(id: String): EntityRef[ShoppingCartCommand] = {
    clusterSharding.entityRefFor(ShoppingCart.typeKey, id)
  }
  // #shopping-cart-entity-ref

  // #shopping-cart-service-call
  implicit val timeout = Timeout(5.seconds)

  override def get(id: String): ServiceCall[NotUsed, ShoppingCartView] = ServiceCall { _ =>
    entityRef(id)
      .ask(reply => Get(reply))
      .map(cartSummary => asShoppingCartView(id, cartSummary))
  }
  // #shopping-cart-service-call

  // #shopping-cart-service-map
  private def asShoppingCartView(id: String, cartSummary: Summary): ShoppingCartView = {
    ShoppingCartView(
      id,
      cartSummary.items.map((ShoppingCartItem.apply _).tupled).toSeq,
      cartSummary.checkedOut
    )
  }
  // #shopping-cart-service-map
}

// #shopping-cart-service-view
final case class ShoppingCartItem(itemId: String, quantity: Int)
final case class ShoppingCartView(id: String, items: Seq[ShoppingCartItem], checkedOut: Boolean)

object ShoppingCartItem {
  implicit val format: Format[ShoppingCartItem] = Json.format
}

object ShoppingCartView {
  implicit val format: Format[ShoppingCartView] = Json.format
}
// #shopping-cart-service-view
