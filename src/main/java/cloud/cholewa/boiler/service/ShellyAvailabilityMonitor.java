package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyCallListener;
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
 * Tells the household when the Shelly of the boiler room stops working: an alert once every call
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
public class ShellyAvailabilityMonitor implements ShellyCallListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEVICE = "The Shelly in the boiler room";
    private static final String CONSEQUENCE = " The furnace and the pumps are not being controlled.";

    private final NotificationPublisher notificationPublisher;
    private final ShellyMonitorProperties properties;
    private final Clock clock;

    //the first failed call since the device last answered; null while it answers
    private Instant failingSince;
    //whether a call failed since the monitor was last asked: an alert takes fresh evidence, a
    //pass that did not call the device at all says nothing about it
    private boolean failedSinceLastReport;
    //the start of the outage the household was told about; null when there is nothing to take back
    private Instant reportedOutageSince;
    //the first answer after that outage was reported; it ends there, whatever happens next
    private Instant reportedOutageEndedAt;
    private Instant lastReportAt;

    @Override
    public synchronized void recordAnswer() {
        failingSince = null;
        if (reportedOutageSince != null && reportedOutageEndedAt == null) {
            reportedOutageEndedAt = clock.instant();
        }
    }

    @Override
    public synchronized void recordFailure() {
        failedSinceLastReport = true;
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
        if (state.reportedOutageSince() == null) {
            return state.failingSince() != null
                && state.failedSinceLastReport()
                && lastsAtLeast(state.failingSince(), now, properties.offlineAfter())
                ? notificationPublisher.publishAlert(alertText(state.failingSince(), now))
                    .doOnSuccess(unused -> reported(state.failingSince(), now))
                : Mono.empty();
        }
        //before anything else about the device: the outage that was announced is over, even when
        //the calls already fail again - that is a new outage, counted from its own first failure
        if (state.reportedOutageEndedAt() != null) {
            return notificationPublisher
                .publishInfo(recoveryText(state.reportedOutageSince(), state.reportedOutageEndedAt()))
                .doOnSuccess(unused -> recovered());
        }
        return state.failedSinceLastReport() && lastsAtLeast(state.lastReportAt(), now, properties.reminderInterval())
            ? notificationPublisher.publishReminder(reminderText(state.reportedOutageSince(), now))
                .doOnSuccess(unused -> reminded(now))
            : Mono.empty();
    }

    private synchronized State snapshot() {
        final State state = new State(
            failingSince, failedSinceLastReport, reportedOutageSince, reportedOutageEndedAt, lastReportAt);
        failedSinceLastReport = false;
        return state;
    }

    private synchronized void reported(final Instant outageSince, final Instant now) {
        reportedOutageSince = outageSince;
        reportedOutageEndedAt = null;
        lastReportAt = now;
    }

    private synchronized void reminded(final Instant now) {
        lastReportAt = now;
    }

    private synchronized void recovered() {
        reportedOutageSince = null;
        reportedOutageEndedAt = null;
        lastReportAt = null;
    }

    private static boolean lastsAtLeast(final Instant since, final Instant now, final Duration limit) {
        return !Duration.between(since, now).minus(limit).isNegative();
    }

    //"failed every call", not "is offline": a device that answers 401 or 500 to everything is not
    //driven either, and whoever reads the alert should not look for a power cut only
    private String alertText(final Instant since, final Instant now) {
        return DEVICE + " has failed every call since " + time(since) + " (" + span(since, now) + ")."
            + CONSEQUENCE;
    }

    private String reminderText(final Instant since, final Instant now) {
        return DEVICE + " is still failing every call - since " + time(since) + " (" + span(since, now) + ")."
            + CONSEQUENCE;
    }

    private String recoveryText(final Instant since, final Instant until) {
        return DEVICE + " answers again. Its calls failed from " + time(since) + " to " + time(until)
            + " (" + span(since, until) + ").";
    }

    private String time(final Instant instant) {
        return TIME.format(instant.atZone(clock.getZone()));
    }

    private static String span(final Instant since, final Instant until) {
        final Duration duration = Duration.between(since, until);
        final long hours = duration.toHours();
        final int minutes = duration.toMinutesPart();

        return hours == 0 ? minutes + " min" : hours + " h " + minutes + " min";
    }

    private record State(
        Instant failingSince,
        boolean failedSinceLastReport,
        Instant reportedOutageSince,
        Instant reportedOutageEndedAt,
        Instant lastReportAt
    ) {
    }
}
