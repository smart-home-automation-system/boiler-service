package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.config.ShellyMonitorProperties;
import cloud.cholewa.boiler.rabbit.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Tells the household when the Shelly of the boiler room stops answering: an alert once every call
 * has failed for {@code offlineAfter}, a reminder every {@code reminderInterval} while that lasts,
 * and one info when the device answers again.
 * <p>
 * The state lives in memory, like the rest of this service. After a restart in the middle of an
 * outage the new instance counts from its own first failed call, so the outage is announced a
 * second time, as an alert - a duplicate, never a silence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShellyAvailabilityMonitor {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEVICE = "The Shelly in the boiler room";
    private static final String CONSEQUENCE = " The furnace and the pumps are not being controlled.";

    private final NotificationPublisher notificationPublisher;
    private final ShellyMonitorProperties properties;
    private final Clock clock;

    //the first failed call since the device last answered; null while it answers
    private Instant failingSince;
    //the start of the outage the household was told about; null when there is nothing to take back
    private Instant reportedOutageSince;
    private Instant lastReportAt;

    /** The device answered a call, whatever the call was. */
    public synchronized void recordAnswer() {
        failingSince = null;
    }

    /** A call to the device failed: no connection, no answer in time, or an answer that is not one. */
    public synchronized void recordFailure() {
        if (failingSince == null) {
            failingSince = clock.instant();
        }
    }

    /**
     * Publishes what is due, if anything. Never signals an error: a notification that could not be
     * published must not fail the control pass it follows. The state moves on only once the broker
     * has taken the message, so a failed one is tried again with the next pass.
     */
    public Mono<Void> report() {
        //deferred: the reactive @Scheduled method calling this is invoked once and resubscribed for
        //every run, so the time and the state have to be read at subscription
        return Mono.defer(() -> due(snapshot(), clock.instant()))
            .onErrorResume(throwable -> {
                log.error(
                    "Notification about the boiler room Shelly not published, it is tried again with the next pass:"
                        + " {}: {}",
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage()
                );
                return Mono.empty();
            });
    }

    private Mono<Void> due(final State state, final Instant now) {
        if (state.failingSince() == null) {
            return state.reportedOutageSince() == null
                ? Mono.empty()
                : notificationPublisher.publishInfo(recoveryText(state.reportedOutageSince(), now))
                    .doOnSuccess(unused -> recovered());
        }
        if (state.reportedOutageSince() == null) {
            return lastsAtLeast(state.failingSince(), now, properties.offlineAfter())
                ? notificationPublisher.publishAlert(alertText(state.failingSince(), now))
                    .doOnSuccess(unused -> reported(state.failingSince(), now))
                : Mono.empty();
        }
        return lastsAtLeast(state.lastReportAt(), now, properties.reminderInterval())
            ? notificationPublisher.publishReminder(reminderText(state.reportedOutageSince(), now))
                .doOnSuccess(unused -> reminded(now))
            : Mono.empty();
    }

    private synchronized State snapshot() {
        return new State(failingSince, reportedOutageSince, lastReportAt);
    }

    private synchronized void reported(final Instant outageSince, final Instant now) {
        reportedOutageSince = outageSince;
        lastReportAt = now;
    }

    private synchronized void reminded(final Instant now) {
        lastReportAt = now;
    }

    private synchronized void recovered() {
        reportedOutageSince = null;
        lastReportAt = null;
    }

    private static boolean lastsAtLeast(final Instant since, final Instant now, final Duration limit) {
        return !Duration.between(since, now).minus(limit).isNegative();
    }

    private String alertText(final Instant since, final Instant now) {
        return DEVICE + " has not answered since " + time(since) + " (" + span(since, now) + ")." + CONSEQUENCE;
    }

    private String reminderText(final Instant since, final Instant now) {
        return DEVICE + " is still not answering - since " + time(since) + " (" + span(since, now) + ")."
            + CONSEQUENCE;
    }

    private String recoveryText(final Instant since, final Instant now) {
        return DEVICE + " answers again. It was silent from " + time(since) + " to " + time(now)
            + " (" + span(since, now) + ").";
    }

    private String time(final Instant instant) {
        return TIME.format(instant.atZone(clock.getZone()));
    }

    private static String span(final Instant since, final Instant now) {
        final Duration duration = Duration.between(since, now);
        final long hours = duration.toHours();
        final int minutes = duration.toMinutesPart();

        return hours == 0 ? minutes + " min" : hours + " h " + minutes + " min";
    }

    private record State(Instant failingSince, Instant reportedOutageSince, Instant lastReportAt) {
    }
}
