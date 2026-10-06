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
 * Tells the household when the Shelly of the boiler room stops working: an alert once its calls
 * have been failing for {@code offlineAfter}, a reminder every {@code reminderInterval} while that
 * lasts, and one info when the device works again.
 * <p>
 * It judges the device by whole control passes, not by single calls. A pass in which any call
 * failed is a failed pass - the device may answer its status and refuse every command, or answer
 * one call in ten, and is not driven either way. Only a pass in which the device was called and
 * nothing failed says it works. A pass that did not call the device says nothing.
 * <p>
 * The state lives in memory, like the rest of this service. After a restart in the middle of an
 * outage the new instance counts from its own first failed call, so the outage is announced a
 * second time, as an alert - a duplicate, never a silence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShellyAvailabilityMonitor implements ShellyCallListener {

    //the publisher bounds the wait for the broker's confirm, not a send that hangs before it - on
    //a blocked connection, on a channel that does not open. Whatever happens there, the control
    //pass waiting for this report goes on after this long
    static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(30);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEVICE = "The Shelly in the boiler room";
    private static final String CONSEQUENCE = " The furnace and the pumps may not be controlled.";

    private final NotificationPublisher notificationPublisher;
    private final ShellyMonitorProperties properties;
    private final Clock clock;

    //what the calls of the pass in progress have shown so far
    private boolean failedInPass;
    private boolean answeredInPass;
    //the first failed call since the last pass without one; null while the device works
    private Instant failingSince;
    //the start of the outage the household was told about; null when there is nothing to take back
    private Instant reportedOutageSince;
    //the end of that outage: the first pass without a failed call after it was reported
    private Instant reportedOutageEndedAt;
    private Instant lastReportAt;

    @Override
    public synchronized void recordAnswer() {
        answeredInPass = true;
    }

    @Override
    public synchronized void recordFailure() {
        failedInPass = true;
        if (failingSince == null) {
            failingSince = clock.instant();
        }
    }

    /**
     * Closes the control pass and publishes what is due, if anything. Never signals an error and
     * never takes longer than {@link #PUBLISH_TIMEOUT}: a notification must neither fail nor stall
     * the control of the furnace. The state moves on only once the broker has taken the message,
     * so a failed one is tried again with the next failed pass.
     */
    public Mono<Void> report() {
        //deferred: the reactive @Scheduled method calling this is invoked once and resubscribed for
        //every run, so the time and the state have to be read at subscription
        return Mono.defer(() -> due(closePass(clock.instant())).timeout(PUBLISH_TIMEOUT))
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

    private Mono<Void> due(final Pass pass) {
        if (pass.reportedOutageSince() == null) {
            return pass.failed() && lastsAtLeast(pass.failingSince(), pass.closedAt(), properties.offlineAfter())
                ? notificationPublisher.publishAlert(alertText(pass.failingSince(), pass.closedAt()))
                    .doOnSuccess(unused -> reported(pass.failingSince(), pass.closedAt()))
                : Mono.empty();
        }
        //before anything else: the outage that was announced is over, and the household hears it
        //even when this pass failed again - that is a new outage, counted from its own first failure
        if (pass.reportedOutageEndedAt() != null) {
            return notificationPublisher
                .publishInfo(recoveryText(pass.reportedOutageSince(), pass.reportedOutageEndedAt()))
                .doOnSuccess(unused -> recovered());
        }
        return pass.failed() && lastsAtLeast(pass.lastReportAt(), pass.closedAt(), properties.reminderInterval())
            ? notificationPublisher.publishReminder(reminderText(pass.reportedOutageSince(), pass.closedAt()))
                .doOnSuccess(unused -> reminded(pass.closedAt()))
            : Mono.empty();
    }

    private synchronized Pass closePass(final Instant now) {
        final boolean failed = failedInPass;
        final boolean clean = answeredInPass && !failedInPass;
        failedInPass = false;
        answeredInPass = false;

        if (clean) {
            failingSince = null;
            if (reportedOutageSince != null && reportedOutageEndedAt == null) {
                reportedOutageEndedAt = now;
            }
        }
        return new Pass(now, failed, failingSince, reportedOutageSince, reportedOutageEndedAt, lastReportAt);
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

    //"failing calls", not "offline": a device that answers 401 to every command, or one call in
    //ten, is not driven either, and whoever reads the alert should not look for a power cut only
    private String alertText(final Instant since, final Instant now) {
        return DEVICE + " has been failing calls since " + time(since) + " (" + span(since, now) + ")."
            + CONSEQUENCE;
    }

    private String reminderText(final Instant since, final Instant now) {
        return DEVICE + " is still failing calls - since " + time(since) + " (" + span(since, now) + ")."
            + CONSEQUENCE;
    }

    private String recoveryText(final Instant since, final Instant until) {
        return DEVICE + " works again. Its calls failed from " + time(since) + " to " + time(until)
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

    private record Pass(
        Instant closedAt,
        boolean failed,
        Instant failingSince,
        Instant reportedOutageSince,
        Instant reportedOutageEndedAt,
        Instant lastReportAt
    ) {
    }
}
