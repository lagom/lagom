/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

import akka.stream.javadsl.Source;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import org.pcollections.PSequence;

import static com.lightbend.lagom.javadsl.api.Service.*;

public class FirstDescriptor {

  public interface CallIdName extends Service {

    ServiceCall<String, String> sayHello();

    // #call-id-name
    default Descriptor descriptor() {
      return named("hello").withCalls(namedCall("hello", this::sayHello));
    }
    // #call-id-name
  }

  public interface CallLongId extends Service {
    // #call-long-id
    ServiceCall<NotUsed, Order> getOrder(long id);

    default Descriptor descriptor() {
      return named("orders").withCalls(pathCall("/order/:id", this::getOrder));
    }
    // #call-long-id
  }

  public interface CallComplexItemId extends Service {
    // #call-complex-item-id
    ServiceCall<NotUsed, Item> getItem(long orderId, String itemId);

    default Descriptor descriptor() {
      return named("orders").withCalls(pathCall("/order/:orderId/item/:itemId", this::getItem));
    }
    // #call-complex-item-id
  }

  public interface CallQueryStringParameters extends Service {
    // #call-query-string-parameters
    ServiceCall<NotUsed, PSequence<Item>> getItems(long orderId, int pageNo, int pageSize);

    default Descriptor descriptor() {
      return named("orders")
          .withCalls(pathCall("/order/:orderId/items?pageNo&pageSize", this::getItems));
    }
    // #call-query-string-parameters
  }

  public interface CallRest extends Service {
    // #call-rest
    ServiceCall<Item, NotUsed> addItem(long orderId);

    ServiceCall<NotUsed, Item> getItem(long orderId, String itemId);

    ServiceCall<NotUsed, NotUsed> deleteItem(long orderId, String itemId);

    default Descriptor descriptor() {
      return named("orders")
          .withCalls(
              restCall(Method.POST, "/order/:orderId/item", this::addItem),
              restCall(Method.GET, "/order/:orderId/item/:itemId", this::getItem),
              restCall(Method.DELETE, "/order/:orderId/item/:itemId", this::deleteItem));
    }
    // #call-rest
  }

  public interface CallStream extends Service {
    // #call-stream
    ServiceCall<String, Source<String, ?>> tick(int interval);

    default Descriptor descriptor() {
      return named("clock").withCalls(pathCall("/tick/:interval", this::tick));
    }
    // #call-stream
  }

  public interface HelloStream extends Service {
    // #hello-stream
    ServiceCall<Source<String, ?>, Source<String, ?>> sayHello();

    default Descriptor descriptor() {
      return named("hello").withCalls(call(this::sayHello));
    }
    // #hello-stream
  }
}
