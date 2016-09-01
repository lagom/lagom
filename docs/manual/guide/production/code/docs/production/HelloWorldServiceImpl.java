


//#content
import com.lightbend.lagom.HelloWorldService;

public class HelloWorldServiceImpl implements HelloWorldService {

    @Override
    public ServiceCall<Greeting, String> sayHello() {
        return request -> completedFuture("Hello " + request.getMessage() );
    }

}
//#content
