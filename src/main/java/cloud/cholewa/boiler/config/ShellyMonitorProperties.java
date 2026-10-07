package cloud.cholewa.boiler.config;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * {@code offlineAfter} - how long the Shelly of the boiler room may fail every call before it is
 * reported. Long enough for a restart or a firmware update of the device to pass unnoticed.<br>
 * {@code reminderInterval} - how often the alert is repeated while the device stays silent.<br>
 * A bare number is minutes: without the unit it would bind as milliseconds, and "5" would report
 * the device after its first failed call. The upper bounds keep a typo ("5h" for "5m") from
 * switching the alert off for practical purposes.
 */
@Validated
@ConfigurationProperties(prefix = "boiler.shelly-monitor")
public record ShellyMonitorProperties(
    @NotNull @DurationMin(minutes = 2) @DurationMax(hours = 1)
    @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("5m") Duration offlineAfter,
    @NotNull @DurationMin(minutes = 5) @DurationMax(hours = 24)
    @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("1h") Duration reminderInterval
) {
}
