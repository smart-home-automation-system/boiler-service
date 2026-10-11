package cloud.cholewa.boiler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

//The password of the broker comes from the environment and has no default, so that a pod without
//it does not start. Left to Spring Boot it would: the binder keeps a placeholder it cannot resolve
//as its text, the password becomes the string "${rabbitmq-password}", the pod turns Ready, and the
//broker refuses the first notification - which is sent when something is already wrong (HAS-217).
//The environment itself is strict about the same placeholder, so asking it is the whole check
@Configuration(proxyBeanMethods = false)
public class BrokerPasswordCheck {

    static final String PASSWORD = "spring.rabbitmq.password";

    public BrokerPasswordCheck(final Environment environment) {
        require(environment, PASSWORD);
    }

    private static void require(final Environment environment, final String property) {
        final String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException e) {
            //Spring's message names the variable: Could not resolve placeholder 'rabbitmq-password' ...
            throw new IllegalStateException(
                "The password of the broker is not set (" + property + "): " + e.getMessage(), e);
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("The password of the broker is empty (" + property + ")");
        }
    }
}
