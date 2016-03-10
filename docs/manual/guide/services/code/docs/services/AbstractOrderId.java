package docs.services;

import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

//#order-id
@Value.Immutable
@ImmutableStyle
public interface AbstractOrderId {
    @Value.Parameter
    long id();
}
//#order-id
