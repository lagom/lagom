package docs.services

import java.lang.Long
import java.util.concurrent.TimeUnit

import com.lightbend.lagom.docs.ServiceSupport
import akka.NotUsed
import com.lightbend.lagom.javadsl.api.ServiceCall
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader
import docs.services.FirstDescriptor._
import docs.services.simpleitemid.Descriptors.{CallServiceItemId, CallSimpleItemId}

import scala.concurrent.Future

class ServiceDescriptorsSpec extends ServiceSupport {

  "Service descriptors documentation" should {
    "show a hello service" in withServiceInstance[HelloService](new HelloService {
      override def sayHello(): ServiceCall[NotUsed, String, String] = serviceCall { (_, name) =>
        Future.successful("Hello " + name)
      }
    }).apply { app => client =>
      client.sayHello().invoke("docs").toCompletableFuture.get(10, TimeUnit.SECONDS) should ===("Hello docs")
    }

    "show naming a service call" in withServiceInstance[CallIdName](new CallIdName {
      override def sayHello(): ServiceCall[NotUsed, String, String] = serviceCall { (header, _, name) =>
        Future.successful((ResponseHeader.OK, header.uri().getPath + " " + name))
      }
    }).apply { app => client =>
      client.sayHello().invoke("docs").toCompletableFuture.get(10, TimeUnit.SECONDS) should ===("/hello docs")
    }

    "show giving a service call an id" in withServiceInstance[CallLongId](new CallLongId {
      override def getOrder: ServiceCall[Long, NotUsed, Order] = serviceCall { (id, _) =>
        Future.successful(Order.of(id))
      }
    }).apply { app => client =>
      client.getOrder.invoke(10, NotUsed).toCompletableFuture.get(10, TimeUnit.SECONDS).id should ===(10)
    }

    "show giving a service a simple structured id" in withServiceInstance[CallSimpleItemId](new CallSimpleItemId {
      override def getItem: ServiceCall[simpleitemid.ItemId, NotUsed, Item] = serviceCall { (id, _) =>
        Future.successful(Item.of(id.itemId, id.orderId))
      }
    }).apply { app => client =>
      val item = client.getItem.invoke(simpleitemid.ItemId.of(10, 20), NotUsed).toCompletableFuture.get(10, TimeUnit.SECONDS)
      item.id should ===(20)
      item.orderId should ===(10)
    }

    "show configuring id serializers at the service level" in withServiceInstance[CallServiceItemId](new CallServiceItemId {
      override def getItemHistory: ServiceCall[simpleitemid.ItemId, NotUsed, ItemHistory] = serviceCall { (_, _) => ??? }
      override def getItem: ServiceCall[simpleitemid.ItemId, NotUsed, Item] = serviceCall { (id, _) =>
        Future.successful(Item.of(id.itemId, id.orderId))
      }
    }).apply { app => client =>
      val item = client.getItem.invoke(simpleitemid.ItemId.of(10, 20), NotUsed).toCompletableFuture.get(10, TimeUnit.SECONDS)
      item.id should ===(20)
      item.orderId should ===(10)
    }

    "show configuring complex ids" in withServiceInstance[CallComplexItemId](new CallComplexItemId {
      override def getOrder: ServiceCall[OrderId, NotUsed, Order] = serviceCall { (_, _) => ??? }
      override def getItemHistory: ServiceCall[ItemId, NotUsed, ItemHistory] = serviceCall { (_, _) => ??? }
      override def getItem: ServiceCall[ItemId, NotUsed, Item] = serviceCall { (id, _) =>
        Future.successful(Item.of(id.itemId, id.orderId.id))
      }
    }).apply { app => client =>
      val item = client.getItem.invoke(ItemId.of(OrderId.of(10), 20), NotUsed).toCompletableFuture.get(10, TimeUnit.SECONDS)
      item.id should ===(20)
      item.orderId should ===(10)
    }

    "show configuring rest calls" in withServiceInstance[CallRest](new CallRest {
      override def addItem(): ServiceCall[OrderId, Item, NotUsed] = serviceCall { (_, _) => ??? }
      override def deleteItem(): ServiceCall[ItemId, NotUsed, NotUsed] = serviceCall { (_, _) => ???}
      override def getItem: ServiceCall[ItemId, NotUsed, Item] = serviceCall { (id, _) =>
        Future.successful(Item.of(id.itemId, id.orderId.id))
      }
    }).apply { app => client =>
      val item = client.getItem.invoke(ItemId.of(OrderId.of(10), 20), NotUsed).toCompletableFuture.get(10, TimeUnit.SECONDS)
      item.id should ===(20)
      item.orderId should ===(10)
    }

  }

}
