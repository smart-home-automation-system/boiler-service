package cloud.cholewa.boiler.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

//application.yaml as the profiles read it, with the properties of the broker and nothing that
//could open a connection. The active profiles are given here, above the system property surefire
//sets for every test class
class BrokerPasswordCheckTest {

    private static final AtomicBoolean BEAN_CREATED = new AtomicBoolean();

    //without the variables of the machine: a developer who exported the password for a local run
    //would otherwise see the tests of the missing password fail
    private final ApplicationContextRunner withoutTheCheck = new ApplicationContextRunner()
        .withInitializer(context -> context.getEnvironment().getPropertySources()
            .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(BrokerProperties.class);

    private final ApplicationContextRunner contextRunner = withoutTheCheck
        .withUserConfiguration(BrokerPasswordCheck.class);

    @BeforeEach
    void setUp() {
        BEAN_CREATED.set(false);
    }

    //why the check exists: nothing in Spring Boot minds the missing variable. It also ties the
    //name in the check to the placeholder of application.yaml
    @Test
    void should_take_the_placeholder_for_the_password_when_nothing_checks() {
        withoutTheCheck
            .withPropertyValues("spring.profiles.active=home")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(BrokerPasswordCheck.PASSWORDS).containsExactly(
                    new BrokerPasswordCheck.Password("spring.rabbitmq.password", "rabbitmq-password"));
                assertThat(context.getBean(RabbitProperties.class).getPassword())
                    .isEqualTo("${rabbitmq-password}");
            });
    }

    @Test
    void should_not_start_in_the_cluster_without_the_password() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home")
            .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining(
                "No password of the broker: spring.rabbitmq.password has no value (set rabbitmq-password)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void should_not_start_with_an_empty_password(final String password) {
        contextRunner
            .withPropertyValues("spring.profiles.active=home", "rabbitmq-password=" + password)
            .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining(
                "No password of the broker: spring.rabbitmq.password has no value"));
    }

    //refused before the first bean is created - before anything is scheduled or connected
    @Test
    void should_refuse_the_start_before_any_bean_is_created() {
        contextRunner
            .withUserConfiguration(SomeBean.class)
            .withPropertyValues("spring.profiles.active=home")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(BEAN_CREATED).isFalse();
            });
    }

    @Test
    void should_create_the_beans_once_the_password_is_there() {
        contextRunner
            .withUserConfiguration(SomeBean.class)
            .withPropertyValues("spring.profiles.active=home", "rabbitmq-password=publisherSecret")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(BEAN_CREATED).isTrue();
            });
    }

    //a password with "${" in it does not resolve - and must not be quoted by the refusal
    @Test
    void should_not_repeat_a_password_it_cannot_resolve() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home", "rabbitmq-password=topSecret${left}Right")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(stackTraceOf(context.getStartupFailure()))
                    .contains("spring.rabbitmq.password has no value")
                    .doesNotContain("topSecret", "Right", "left");
            });
    }

    @Test
    void should_start_in_the_cluster_with_the_password_of_the_deployment() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home", "rabbitmq-password=publisherSecret")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(RabbitProperties.class).getPassword()).isEqualTo("publisherSecret");
            });
    }

    //the property set directly, above application.yaml: its placeholder is then never read
    @Test
    void should_start_with_the_password_given_as_the_property_itself() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home", "spring.rabbitmq.password=publisherSecret")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(RabbitProperties.class).getPassword()).isEqualTo("publisherSecret");
            });
    }

    //a run on a workstation publishes nothing unless it is given the real password
    @Test
    void should_start_on_a_workstation_without_the_password() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home,local")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(RabbitProperties.class).getPassword()).isEqualTo("dummyPassword");
            });
    }

    @Test
    void should_take_the_password_given_to_a_run_on_a_workstation() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home,local", "rabbitmq-password=publisherSecret")
            .run(context ->
                assertThat(context.getBean(RabbitProperties.class).getPassword()).isEqualTo("publisherSecret"));
    }

    @Test
    void should_start_under_the_test_profile() {
        contextRunner
            .withPropertyValues("spring.profiles.active=test")
            .run(context -> assertThat(context).hasNotFailed());
    }

    private static String stackTraceOf(final Throwable failure) {
        final StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }

    @Configuration
    @EnableConfigurationProperties(RabbitProperties.class)
    static class BrokerProperties {
    }

    @Configuration
    static class SomeBean {

        @Bean
        Object someBean() {
            BEAN_CREATED.set(true);
            return new Object();
        }
    }
}
