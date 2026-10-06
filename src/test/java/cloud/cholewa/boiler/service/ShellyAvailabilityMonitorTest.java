package cloud.cholewa.boiler.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cloud.cholewa.boiler.client.ShellyCall;
import cloud.cholewa.boiler.config.ShellyMonitorProperties;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.boiler.rabbit.NotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Every test is a sequence of control passes: the calls of a pass tell the monitor how they ended,
 * and the pass ends by asking it for its report.
 */
@ExtendWith(MockitoExtension.class)
class ShellyAvailabilityMonitorTest {

    private static final String CONSEQUENCE = " The furnace and the pumps may not be controlled.";
    private static final String ALERT_AFTER_5_MIN = "The Shelly in the boiler room has been failing calls since"
        + " 2026-10-06 22:00 (5 min)." + CONSEQUENCE;

    @Mock
    private NotificationPublisher notificationPublisher;

    //22:00 in Warsaw
    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-06T20:00:00Z"));

    private ShellyAvailabilityMonitor sut;

    @BeforeEach
    void setUp() {
        sut = new ShellyAvailabilityMonitor(
            notificationPublisher,
            new ShellyMonitorProperties(Duration.ofMinutes(5), Duration.ofHours(1)),
            clock
        );
    }

    @Test
    void should_publish_nothing_while_the_device_works() {
        cleanPass();
        after(Duration.ofHours(3));
        cleanPass();

        verifyNoInteractions(notificationPublisher);
    }

    //a restart or a firmware update of the device has to pass unnoticed
    @Test
    void should_publish_nothing_before_the_calls_have_been_failing_for_the_limit() {
        failedPass();
        after(Duration.ofMinutes(4).plusSeconds(59));
        failedPass();

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void should_alert_once_when_the_calls_have_been_failing_for_the_limit() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();
        after(Duration.ofMinutes(1));
        failedPass();
        after(Duration.ofMinutes(1));
        failedPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verifyNoMoreInteractions(notificationPublisher);
    }

    //a pass without a failed call in between and the count starts again
    @Test
    void should_count_from_the_first_failure_after_the_last_pass_without_one() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        failedPass();
        after(Duration.ofMinutes(4));
        cleanPass();
        failedPass();
        after(Duration.ofMinutes(4));
        failedPass();

        verifyNoInteractions(notificationPublisher);

        after(Duration.ofMinutes(1));
        failedPass();

        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has been failing calls since"
            + " 2026-10-06 22:04 (5 min)." + CONSEQUENCE);
    }

    //the status reads answer and every command is refused, or one call in ten gets through: the
    //device is not driven, and single answers must not keep the alert from ever coming
    @Test
    void should_alert_when_every_pass_has_a_failed_call_although_other_calls_are_answered() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        mixedPass();
        after(Duration.ofMinutes(5));
        mixedPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
    }

    //a pass that does not call the device says nothing: the time goes by, but nothing shows the
    //device is still failing
    @Test
    void should_not_alert_or_remind_at_the_end_of_a_pass_that_did_not_call_the_device() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        failedPass();
        after(Duration.ofMinutes(4));
        failedPass();
        after(Duration.ofMinutes(3));
        idlePass();

        verifyNoInteractions(notificationPublisher);

        after(Duration.ofMinutes(1));
        failedPass();
        after(Duration.ofHours(2));
        idlePass();

        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has been failing calls since"
            + " 2026-10-06 22:00 (8 min)." + CONSEQUENCE);
        verifyNoMoreInteractions(notificationPublisher);
    }

    //one failed call that nothing followed for as long as the limit is not an outage going on,
    //and a second one hours later is not its continuation
    @Test
    void should_forget_a_failure_that_nothing_followed() {
        failedPass();
        after(Duration.ofMinutes(5));
        idlePass();
        after(Duration.ofHours(3));
        failedPass();

        verifyNoInteractions(notificationPublisher);
    }

    //the commands are refused and the status reads answer: a pass that had nothing to switch
    //shows only answers, and proves nothing about the commands
    @Test
    void should_not_take_status_answers_for_the_return_of_a_device_that_refused_commands() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        mixedPass();
        after(Duration.ofMinutes(5));
        mixedPass();
        after(Duration.ofMinutes(1));
        statusOnlyPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verifyNoMoreInteractions(notificationPublisher);

        after(Duration.ofMinutes(1));
        commandAnsweredPass();

        verify(notificationPublisher).publishInfo("The Shelly in the boiler room works again."
            + " Its calls failed from 2026-10-06 22:00 to 2026-10-06 22:07 (7 min).");
    }

    //a command is sent only when a relay has to change: without this the outage would stay open
    //for days, and the next one would arrive as a yellow reminder dated from the old start
    @Test
    void should_end_the_outage_when_the_device_answers_and_nothing_has_failed_for_the_limit() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        mixedPass();
        after(Duration.ofMinutes(5));
        mixedPass();
        after(Duration.ofMinutes(4));
        statusOnlyPass();

        verify(notificationPublisher, times(0)).publishInfo(anyString());

        after(Duration.ofMinutes(1));
        statusOnlyPass();

        verify(notificationPublisher).publishInfo("The Shelly in the boiler room works again."
            + " Its calls failed from 2026-10-06 22:00 to 2026-10-06 22:10 (10 min).");

        //and what fails after that is a new outage, with a red alert of its own
        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();

        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has been failing calls since"
            + " 2026-10-06 22:10 (5 min)." + CONSEQUENCE);
    }

    //the alert was due and the broker was down; then the passes had nothing to switch. A single
    //failure the next day must not be announced as an outage going on since yesterday
    @Test
    void should_not_date_a_later_failure_from_an_outage_whose_alert_never_got_through() {
        when(notificationPublisher.publishAlert(anyString()))
            .thenReturn(Mono.error(new BoilerException("Notification refused by the broker: down")));
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        mixedPass();
        after(Duration.ofMinutes(6));
        mixedPass();
        after(Duration.ofMinutes(5));
        statusOnlyPass();
        after(Duration.ofHours(26));
        mixedPass();

        verify(notificationPublisher).publishInfo("The Shelly in the boiler room works again."
            + " Its calls failed from 2026-10-06 22:00 to 2026-10-06 22:11 (11 min).");
        verify(notificationPublisher, times(1)).publishAlert(anyString());
    }

    //a status-only pass in between must not restart the count either
    @Test
    void should_keep_counting_across_a_pass_that_did_not_try_what_failed() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        mixedPass();
        after(Duration.ofMinutes(3));
        statusOnlyPass();
        after(Duration.ofMinutes(2));
        mixedPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
    }

    //the device and the broker were down together, and the device came back first: no alert
    //ever got through, but the household still hears that the furnace was not driven
    @Test
    void should_announce_afterwards_an_outage_whose_alert_never_got_through() {
        when(notificationPublisher.publishAlert(anyString()))
            .thenReturn(Mono.error(new BoilerException("Notification refused by the broker: down")));
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();
        after(Duration.ofMinutes(10));
        cleanPass();
        after(Duration.ofMinutes(1));
        cleanPass();

        verify(notificationPublisher).publishInfo("The Shelly in the boiler room works again."
            + " Its calls failed from 2026-10-06 22:00 to 2026-10-06 22:15 (15 min).");
        verify(notificationPublisher, times(1)).publishInfo(anyString());
    }

    @Test
    void should_remind_every_interval_while_the_calls_keep_failing() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishReminder(anyString())).thenReturn(Mono.empty());

        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();
        after(Duration.ofMinutes(59));
        failedPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verifyNoMoreInteractions(notificationPublisher);

        after(Duration.ofMinutes(1));
        failedPass();
        after(Duration.ofMinutes(30));
        failedPass();
        after(Duration.ofMinutes(30));
        failedPass();

        verify(notificationPublisher).publishReminder("The Shelly in the boiler room is still failing calls -"
            + " since 2026-10-06 22:00 (1 h 5 min)." + CONSEQUENCE);
        verify(notificationPublisher).publishReminder("The Shelly in the boiler room is still failing calls -"
            + " since 2026-10-06 22:00 (2 h 5 min)." + CONSEQUENCE);
        verifyNoMoreInteractions(notificationPublisher);
    }

    @Test
    void should_tell_once_when_a_reported_device_works_again() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        reportedOutage();
        after(Duration.ofMinutes(20));
        cleanPass();
        after(Duration.ofMinutes(1));
        cleanPass();

        verify(notificationPublisher).publishInfo("The Shelly in the boiler room works again."
            + " Its calls failed from 2026-10-06 22:00 to 2026-10-06 22:25 (25 min).");
        verify(notificationPublisher, times(1)).publishAlert(anyString());
        verifyNoMoreInteractions(notificationPublisher);
    }

    //a device that gets one call through now and then is not back: a green message every few
    //minutes, each followed by a new red one, would say the opposite of what is going on
    @Test
    void should_not_announce_the_return_of_a_device_that_still_fails_calls() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        reportedOutage();
        after(Duration.ofMinutes(1));
        mixedPass();
        after(Duration.ofMinutes(1));
        mixedPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verifyNoMoreInteractions(notificationPublisher);
    }

    //nobody was told it was gone, so nobody is told it is back
    @Test
    void should_not_announce_the_return_of_a_device_that_was_never_reported() {
        failedPass();
        after(Duration.ofMinutes(3));
        cleanPass();

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void should_alert_again_about_a_new_outage_after_a_return() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        reportedOutage();
        cleanPass();
        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has been failing calls since"
            + " 2026-10-06 22:05 (5 min)." + CONSEQUENCE);
    }

    //the pass this follows must not fail, and a message the broker did not take is not "sent":
    //the next pass tries the same alert again, as an alert, not as a reminder an hour later
    @Test
    void should_complete_and_try_again_with_the_next_pass_when_the_alert_cannot_be_published() {
        final ListAppender<ILoggingEvent> logs = logs();
        when(notificationPublisher.publishAlert(anyString()))
            .thenReturn(Mono.error(new BoilerException("Notification refused by the broker: no exchange")))
            .thenReturn(Mono.empty());

        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();
        after(Duration.ofMinutes(1));
        failedPass();
        after(Duration.ofMinutes(1));
        failedPass();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has been failing calls since"
            + " 2026-10-06 22:00 (6 min)." + CONSEQUENCE);
        verifyNoMoreInteractions(notificationPublisher);
        assertThat(logs.list)
            .filteredOn(event -> event.getLevel() == Level.ERROR)
            .extracting(ILoggingEvent::getFormattedMessage)
            .containsExactly("Notification about the boiler room Shelly not published, it is tried again with the"
                + " next pass: BoilerException: Notification refused by the broker: no exchange");
    }

    //thrown, not signalled: the publisher is the only part that talks to the broker
    @Test
    void should_complete_when_the_publisher_throws() {
        when(notificationPublisher.publishAlert(anyString())).thenThrow(new IllegalStateException("broken"));

        failedPass();
        after(Duration.ofMinutes(5));
        sut.recordFailure(ShellyCall.STATUS);

        sut.report().as(StepVerifier::create).verifyComplete();
    }

    //a send that hangs on the broker connection: the control pass waiting for the report goes on
    @Test
    void should_complete_when_the_publisher_never_does() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.never());

        failedPass();
        after(Duration.ofMinutes(5));
        sut.recordFailure(ShellyCall.STATUS);

        StepVerifier.withVirtualTime(() -> sut.report())
            .expectSubscription()
            .expectNoEvent(ShellyAvailabilityMonitor.PUBLISH_TIMEOUT.minusSeconds(1))
            .thenAwait(Duration.ofSeconds(1))
            .verifyComplete();
    }

    //the return could not be published and the calls fail again: the household still hears that
    //the first outage ended, and the new one starts counting from its own first failure
    @Test
    void should_announce_the_return_first_when_it_could_not_be_published_before_a_new_outage() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString()))
            .thenReturn(Mono.error(new BoilerException("Notification was not routed to any queue: NO_ROUTE")))
            .thenReturn(Mono.empty());

        reportedOutage();
        cleanPass();
        after(Duration.ofMinutes(1));
        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();

        verify(notificationPublisher, times(2)).publishInfo("The Shelly in the boiler room works again."
            + " Its calls failed from 2026-10-06 22:00 to 2026-10-06 22:05 (5 min).");
        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has been failing calls since"
            + " 2026-10-06 22:06 (5 min)." + CONSEQUENCE);
    }

    //the reactive @Scheduled method calls report() once and subscribes to the same Mono for every
    //run - a time or a state read while the Mono is built would be the one of the pod's start
    @Test
    void should_read_the_time_and_the_state_at_every_subscription_of_the_same_mono() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        final Mono<Void> scheduled = sut.report();

        scheduled.as(StepVerifier::create).verifyComplete();
        sut.recordFailure(ShellyCall.STATUS);
        scheduled.as(StepVerifier::create).verifyComplete();

        verifyNoInteractions(notificationPublisher);

        after(Duration.ofMinutes(5));
        sut.recordFailure(ShellyCall.STATUS);
        scheduled.as(StepVerifier::create).verifyComplete();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
    }

    //two failed passes five minutes apart, the second one publishing the alert: 22:00 - 22:05
    private void reportedOutage() {
        failedPass();
        after(Duration.ofMinutes(5));
        failedPass();
    }

    private void failedPass() {
        sut.recordFailure(ShellyCall.STATUS);
        report();
    }

    private void cleanPass() {
        sut.recordAnswer(ShellyCall.STATUS);
        report();
    }

    //what a device answering its status and refusing a command looks like
    private void mixedPass() {
        sut.recordAnswer(ShellyCall.STATUS);
        sut.recordFailure(ShellyCall.COMMAND);
        sut.recordAnswer(ShellyCall.STATUS);
        report();
    }

    //the same device in a pass that had nothing to switch
    private void statusOnlyPass() {
        sut.recordAnswer(ShellyCall.STATUS);
        report();
    }

    private void commandAnsweredPass() {
        sut.recordAnswer(ShellyCall.STATUS);
        sut.recordAnswer(ShellyCall.COMMAND);
        report();
    }

    private void idlePass() {
        report();
    }

    private void report() {
        sut.report().as(StepVerifier::create).verifyComplete();
    }

    private void after(final Duration duration) {
        clock.advance(duration);
    }

    private static ListAppender<ILoggingEvent> logs() {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(ShellyAvailabilityMonitor.class)).addAppender(appender);
        return appender;
    }

    private static final class MovableClock extends Clock {

        private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

        private Instant now;

        private MovableClock(final Instant now) {
            this.now = now;
        }

        void advance(final Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
