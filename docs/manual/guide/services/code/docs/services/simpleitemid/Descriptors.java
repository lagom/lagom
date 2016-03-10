package docs.services.simpleitemid;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.IdSerializers;
import docs.services.Item;
import docs.services.ItemHistory;

import java.util.Arrays;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;

public class Descriptors {

    public interface CallSimpleItemId extends Service {
        //#call-simple-item-id
        ServiceCall<ItemId, NotUsed, Item> getItem();

        default Descriptor descriptor() {
            return named("orders").with(
                    pathCall("/order/:orderId/item/:itemId", getItem())
                            .with(IdSerializers.create("ItemId", ItemId::of,
                                    id -> Arrays.asList(id.orderId(), id.itemId())))
            );
        }
        //#call-simple-item-id
    }

    public interface CallServiceItemId extends Service {

        //#call-service-item-id
        ServiceCall<ItemId, NotUsed, Item> getItem();
        ServiceCall<ItemId, NotUsed, ItemHistory> getItemHistory();

        default Descriptor descriptor() {
            return named("orders").with(
                    pathCall("/order/:orderId/item/:itemId", getItem()),
                    pathCall("/order/:orderId/item/:itemId/history", getItemHistory())
            ).with(ItemId.class, IdSerializers.create("ItemId", ItemId::of,
                    id -> Arrays.asList(id.orderId(), id.itemId())));
        }
        //#call-service-item-id
    }

}
