package cloud.cholewa.boiler.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SchedulingConfig.class);

    //the profiles are set on the environment directly, because surefire activates "test" for the
    //whole JVM through a system property
    @Test
    void should_schedule_the_control_pass_in_the_cluster() {
        contextRunner
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("home"))
            .run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void should_schedule_the_control_pass_in_a_local_run() {
        contextRunner
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("home", "local"))
            .run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void should_not_schedule_the_control_pass_under_the_test_profile() {
        contextRunner
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
            .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
