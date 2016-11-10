package docs.home.immutable;

//#immutable
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;
import org.pcollections.PVector;

@Value.Immutable
@ImmutableStyle
public interface AbstractUser3 {

  String getName();

  PVector<String> getPhoneNumbers();
}
//#immutable
