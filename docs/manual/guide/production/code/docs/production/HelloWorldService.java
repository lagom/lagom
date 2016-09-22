
//#content
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloWorldService extends Service {

    @Override
    default Descriptor descriptor() {
        return named("helloWorld");
    }
}
//#content
        