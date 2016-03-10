package docs.services;

import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

//#item-id
@Value.Immutable
@ImmutableStyle
public interface AbstractItemId {
    @Value.Parameter
    OrderId orderId();
    @Value.Parameter
    long itemId();
}
//#item-id
