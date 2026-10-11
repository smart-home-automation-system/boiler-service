package cloud.cholewa.boiler.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

//application.yaml as the profiles read it, with the properties of the broker and nothing that
//could open a connection. The active profiles are given here, above the system property surefire
//sets for every test class
class BrokerPasswordCheckTest {

    private final ApplicationContextRunner withoutTheCheck = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(BrokerProperties.class);

    private final ApplicationContextRunner contextRunner = withoutTheCheck
        .withUserConfiguration(BrokerPasswordCheck.class);

    //why the check exists: nothing in Spring Boot minds the missing variable
    @Test
    void should_take_the_placeholder_for_the_password_when_nothing_checks() {
        withoutTheCheck
            .withPropertyValues("spring.profiles.active=home")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(RabbitProperties.class).getPassword())
                    .isEqualTo("${rabbitmq-password}");
            });
    }

    @Test
    void should_not_start_in_the_cluster_without_the_password() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("rabbitmq-password"));
    }

    @Test
    void should_name_the_property_and_the_variable_that_is_missing() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home")
            .run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("The password of the broker is not set (spring.rabbitmq.password)")
                .hasStackTraceContaining("Could not resolve placeholder 'rabbitmq-password'"));
    }

    @Test
    void should_not_start_with_an_empty_password() {
        contextRunner
            .withPropertyValues("spring.profiles.active=home", "rabbitmq-password=")
            .run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("The password of the broker is empty (spring.rabbitmq.password)"));
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

    @Configuration
    @EnableConfigurationProperties(RabbitProperties.class)
    static class BrokerProperties {
    }
}
