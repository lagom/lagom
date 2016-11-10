package docs.home.serialization.v1;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

//#add-optional
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = ItemAdded.class)
public interface AbstractItemAdded extends Jsonable {

  String getShoppingCartId();

  String getProductId();

  int getQuantity();

}
//#add-optional
