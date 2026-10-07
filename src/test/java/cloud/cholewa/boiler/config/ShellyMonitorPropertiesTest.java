package cloud.cholewa.boiler.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ShellyMonitorPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(ShellyMonitorConfig.class);

    @Test
    void should_default_to_five_minutes_and_a_reminder_every_hour() {
        contextRunner.run(context -> {
            final ShellyMonitorProperties properties = context.getBean(ShellyMonitorProperties.class);

            assertThat(properties.offlineAfter()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.reminderInterval()).isEqualTo(Duration.ofHours(1));
        });
    }

    //without @DurationUnit a bare number is milliseconds: "5" would report the device after its
    //first failed call, and remind with every pass
    @Test
    void should_read_a_bare_number_as_minutes() {
        contextRunner
            .withPropertyValues(
                "boiler.shelly-monitor.offline-after=10",
                "boiler.shelly-monitor.reminder-interval=30"
            )
            .run(context -> {
                final ShellyMonitorProperties properties = context.getBean(ShellyMonitorProperties.class);

                assertThat(properties.offlineAfter()).isEqualTo(Duration.ofMinutes(10));
                assertThat(properties.reminderInterval()).isEqualTo(Duration.ofMinutes(30));
            });
    }

    @ParameterizedTest(name = "{0}={1} is refused")
    @CsvSource({
        //one failed pass is not an outage
        "offline-after, 1m",
        "offline-after, 90s",
        //a typo for 5m that would switch the alert off for practical purposes
        "offline-after, 5h",
        //a reminder with every few passes
        "reminder-interval, 4m",
        "reminder-interval, 2d"
    })
    void should_refuse_a_value_outside_the_bounds(final String property, final String value) {
        contextRunner
            .withPropertyValues("boiler.shelly-monitor." + property + "=" + value)
            .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest(name = "{0}={1} is accepted")
    @CsvSource({
        "offline-after, 2m",
        "offline-after, 1h",
        "reminder-interval, 5m",
        "reminder-interval, 24h"
    })
    void should_accept_the_bounds_themselves(final String property, final String value) {
        contextRunner
            .withPropertyValues("boiler.shelly-monitor." + property + "=" + value)
            .run(context -> assertThat(context).hasNotFailed());
    }
}
