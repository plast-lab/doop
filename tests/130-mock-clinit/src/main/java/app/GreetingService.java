package app;
import org.springframework.stereotype.Service;

// Test 130: a bean the container creates. Only its constructor is an entry
// point; creating the instance initializes the class first, so its static
// initializer runs and calls Registry.register.
@Service
public class GreetingService {
    static { Registry.register("greeting"); }

    public String greet() { return "hello"; }
}
