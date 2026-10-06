package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyCall;
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
import java.util.EnumSet;
import java.util.Set;

/**
 * Tells the household when the Shelly of the boiler room stops working: an alert once its calls
 * have been failing for {@code offlineAfter}, a reminder every {@code reminderInterval} while that
 * lasts, and one info when the device works again.
 * <p>
 * It judges the device by whole control passes, not by single calls:
 * <ul>
 * <li>a pass in which any call failed is a <b>failed</b> pass - the device may answer its status
 * and refuse every command, or answer one call in ten, and is not driven either way;</li>
 * <li>a pass without a failed call <b>proves</b> the device works when it answered every kind of
 * call that had been failing - status reads say nothing about a device that refuses commands - or,
 * failing that, when it answered and nothing has failed for {@code offlineAfter}: a command is
 * sent only when a relay has to change, and an outage must not stay open for days waiting for one;</li>
 * <li>any other pass - one that did not call the device, or not yet in the way that failed - says
 * nothing: it neither ends an outage nor lets one be announced.</li>
 * </ul>
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
    private final Set<ShellyCall> answeredInPass = EnumSet.noneOf(ShellyCall.class);
    private final Set<ShellyCall> failedInPass = EnumSet.noneOf(ShellyCall.class);

    //the kinds of call that failed since the device was last proven to work, the first of those
    //failures and the latest; empty and null while it works
    private final Set<ShellyCall> failing = EnumSet.noneOf(ShellyCall.class);
    private Instant failingSince;
    private Instant lastFailureAt;
    //the failures have lasted for the limit, at the end of a failed pass, and nobody was told yet
    private boolean alertDue;

    //the start of the outage the household was told about - or, when the alert never got through,
    //is about to hear of afterwards; null when there is nothing to take back
    private Instant reportedOutageSince;
    //the end of that outage: the first pass proving the device works
    private Instant reportedOutageEndedAt;
    private Instant lastReportAt;

    @Override
    public synchronized void recordAnswer(final ShellyCall call) {
        answeredInPass.add(call);
    }

    @Override
    public synchronized void recordFailure(final ShellyCall call) {
        final Instant now = clock.instant();

        failedInPass.add(call);
        failing.add(call);
        lastFailureAt = now;
        if (failingSince == null) {
            failingSince = now;
        }
    }

    /**
     * Closes the control pass and publishes what is due, if anything. Never signals an error and
     * never takes longer than {@link #PUBLISH_TIMEOUT}: a notification must neither fail nor stall
     * the control of the furnace. The state moves on only once the broker has taken the message:
     * an alert or a reminder that failed is tried again at the end of the next failed pass, the
     * news of a return at the end of the next pass, whatever that pass was like.
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
        //before anything else: an outage is over, and the household hears it even when this pass
        //failed again - that is a new outage, counted from its own first failure
        if (pass.reportedOutageEndedAt() != null) {
            return notificationPublisher
                .publishInfo(recoveryText(pass.reportedOutageSince(), pass.reportedOutageEndedAt()))
                .doOnSuccess(unused -> recovered());
        }
        if (pass.reportedOutageSince() == null) {
            return pass.failed() && pass.alertDue()
                ? notificationPublisher.publishAlert(alertText(pass.failingSince(), pass.closedAt()))
                    .doOnSuccess(unused -> reported(pass.failingSince(), pass.closedAt()))
                : Mono.empty();
        }
        return pass.failed() && lastsAtLeast(pass.lastReportAt(), pass.closedAt(), properties.reminderInterval())
            ? notificationPublisher.publishReminder(reminderText(pass.reportedOutageSince(), pass.closedAt()))
                .doOnSuccess(unused -> reminded(pass.closedAt()))
            : Mono.empty();
    }

    private synchronized Pass closePass(final Instant now) {
        final boolean failed = !failedInPass.isEmpty();
        final boolean proven = !failed && !answeredInPass.isEmpty()
            && (answeredInPass.containsAll(failing) || quietFor(properties.offlineAfter(), now));
        answeredInPass.clear();
        failedInPass.clear();

        if (failingSince != null) {
            if (failed) {
                alertDue = alertDue
                    || reportedOutageSince == null && lastsAtLeast(failingSince, now, properties.offlineAfter());
            } else if (proven) {
                endOutage(now);
            } else if (reportedOutageSince == null && !alertDue && quietFor(properties.offlineAfter(), now)) {
                //a failure nothing followed for as long as the limit: not an outage that is going
                //on, and not something a failure hours later should be counted from
                forgetFailures();
            }
        }
        return new Pass(now, failed, alertDue, failingSince, reportedOutageSince, reportedOutageEndedAt, lastReportAt);
    }

    private boolean quietFor(final Duration limit, final Instant now) {
        return lastFailureAt != null && lastsAtLeast(lastFailureAt, now, limit);
    }

    private void endOutage(final Instant now) {
        //the alert was due and never got through - the broker was down as well: the household
        //still hears that the device was not driven, afterwards, with the news that it is back
        if (reportedOutageSince == null && alertDue) {
            reportedOutageSince = failingSince;
        }
        if (reportedOutageSince != null && reportedOutageEndedAt == null) {
            reportedOutageEndedAt = now;
        }
        forgetFailures();
    }

    private void forgetFailures() {
        failing.clear();
        failingSince = null;
        lastFailureAt = null;
        alertDue = false;
    }

    private synchronized void reported(final Instant outageSince, final Instant now) {
        reportedOutageSince = outageSince;
        reportedOutageEndedAt = null;
        lastReportAt = now;
        alertDue = false;
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
        boolean alertDue,
        Instant failingSince,
        Instant reportedOutageSince,
        Instant reportedOutageEndedAt,
        Instant lastReportAt
    ) {
    }
}
