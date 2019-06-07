/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services

import java.util.concurrent.TimeUnit

import com.lightbend.lagom.docs.ServiceSupport
import akka.NotUsed
import com.lightbend.lagom.javadsl.api.ServiceCall
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader
import docs.services.FirstDescriptor._

import scala.concurrent.Future

class ServiceDescriptorsSpec extends ServiceSupport {

  "Service descriptors documentation" should {
    "show a hello service" in withServiceInstance[HelloService](new HelloService {
      override def sayHello(): ServiceCall[String, String] = serviceCall { name =>
        Future.successful("Hello " + name)
      }
    }).apply { app => client =>
      client.sayHello().invoke("docs").toCompletableFuture.get(10, TimeUnit.SECONDS) should ===("Hello docs")
    }

    "show naming a service call" in withServiceInstance[CallIdName](new CallIdName {
      override def sayHello(): ServiceCall[String, String] = serviceCall { (header, name) =>
        Future.successful((ResponseHeader.OK, header.uri().getPath + " " + name))
      }
    }).apply { app => client =>
      client.sayHello().invoke("docs").toCompletableFuture.get(10, TimeUnit.SECONDS) should ===("/hello docs")
    }

    "show giving a service call an id" in withServiceInstance[CallLongId](new CallLongId {
      override def getOrder(id: Long): ServiceCall[NotUsed, Order] = serviceCall { _ =>
        Future.successful(Order.of(id))
      }
    }).apply { app => client =>
      client.getOrder(10).invoke().toCompletableFuture.get(10, TimeUnit.SECONDS).id should ===(10)
    }

    "show configuring complex ids" in withServiceInstance[CallComplexItemId](new CallComplexItemId {
      override def getItem(orderId: Long, itemId: String): ServiceCall[NotUsed, Item] = serviceCall { _ =>
        Future.successful(Item.of(itemId, orderId))
      }
    }).apply { app => client =>
      val item = client.getItem(10, "foo").invoke().toCompletableFuture.get(10, TimeUnit.SECONDS)
      item.id should ===("foo")
      item.orderId should ===(10)
    }

    "show configuring rest calls" in withServiceInstance[CallRest](new CallRest {
      override def addItem(orderId: Long): ServiceCall[Item, NotUsed] = serviceCall { _ =>
        ???
      }
      override def deleteItem(orderId: Long, itemId: String): ServiceCall[NotUsed, NotUsed] = serviceCall { _ =>
        ???
      }
      override def getItem(orderId: Long, itemId: String): ServiceCall[NotUsed, Item] = serviceCall { _ =>
        Future.successful(Item.of(itemId, orderId))
      }
    }).apply { app => client =>
      val item = client.getItem(10, "foo").invoke().toCompletableFuture.get(10, TimeUnit.SECONDS)
      item.id should ===("foo")
      item.orderId should ===(10)
    }

  }

}
