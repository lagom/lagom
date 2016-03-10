package docs.services;

import akka.stream.javadsl.Source;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.IdSerializers;
import com.lightbend.lagom.javadsl.api.transport.Method;

import java.util.Arrays;

import static com.lightbend.lagom.javadsl.api.Service.*;

public class FirstDescriptor {

    public interface CallIdName extends Service {

        ServiceCall<NotUsed, String, String> sayHello();

        //#call-id-name
        default Descriptor descriptor() {
            return named("hello").with(
                    namedCall("hello", sayHello())
            );
        }
        //#call-id-name
    }

    public interface CallLongId extends Service {
        //#call-long-id
        ServiceCall<Long, NotUsed, Order> getOrder();

        default Descriptor descriptor() {
            return named("orders").with(
                    pathCall("/order/:id", getOrder())
            );
        }
        //#call-long-id
    }

    public interface CallComplexItemId extends Service {
        //#call-complex-item-id
        ServiceCall<OrderId, NotUsed, Order> getOrder();
        ServiceCall<ItemId, NotUsed, Item> getItem();
        ServiceCall<ItemId, NotUsed, ItemHistory> getItemHistory();

        default Descriptor descriptor() {
            return named("orders").with(

                    pathCall("/order/:orderId", getOrder()),
                    pathCall("/order/:orderId/item/:itemId", getItem()),
                    pathCall("/order/:orderId/item/:itemId/history", getItemHistory())

            ).with(ItemId.class, IdSerializers.create("ItemId", ItemId::of,
                    id -> Arrays.asList(id.orderId(), id.itemId()))
            ).with(OrderId.class, IdSerializers.create("OrderId", OrderId::of,
                    OrderId::id));
        }
        //#call-complex-item-id
    }

    public interface CallRest extends Service {
        //#call-rest
        ServiceCall<OrderId, Item, NotUsed> addItem();
        ServiceCall<ItemId, NotUsed, Item> getItem();
        ServiceCall<ItemId, NotUsed, NotUsed> deleteItem();

        default Descriptor descriptor() {
            return named("orders").with(

                    restCall(Method.POST,   "/order/:orderId/item",         addItem()),
                    restCall(Method.GET,    "/order/:orderId/item/:itemId", getItem()),
                    restCall(Method.DELETE, "/order/:orderId/item/:itemId", deleteItem())

            ).with(ItemId.class, IdSerializers.create("ItemId", ItemId::of,
                    id -> Arrays.asList(id.orderId(), id.itemId()))
            ).with(OrderId.class, IdSerializers.create("OrderId", OrderId::of,
                    OrderId::id));
        }
        //#call-rest
    }

    public interface CallStream extends Service {
        //#call-stream
        ServiceCall<Integer, String, Source<String, ?>> tick();

        default Descriptor descriptor() {
            return named("clock").with(
                pathCall("/tick/:interval", tick())
            );
        }
        //#call-stream
    }

    public interface HelloStream extends Service {
        //#hello-stream
        ServiceCall<NotUsed, Source<String, ?>, Source<String, ?>> sayHello();

        default Descriptor descriptor() {
            return named("hello").with(
                call(sayHello())
            );
        }
        //#hello-stream
    }

}
