package docs.services.simpleitemid;

//#item-id
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = ItemId.class)
@JsonSerialize(as = ItemId.class)
public interface AbstractItemId {
    @Value.Parameter
    long orderId();
    @Value.Parameter
    long itemId();
}
//#item-id
