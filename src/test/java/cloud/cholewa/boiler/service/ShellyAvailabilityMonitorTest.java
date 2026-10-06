package cloud.cholewa.boiler.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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

@ExtendWith(MockitoExtension.class)
class ShellyAvailabilityMonitorTest {

    private static final String ALERT_AFTER_5_MIN = "The Shelly in the boiler room has not answered since"
        + " 2026-10-06 22:00 (5 min). The furnace and the pumps are not being controlled.";

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
    void should_publish_nothing_while_the_device_answers() {
        sut.recordAnswer();
        report();
        clock.advance(Duration.ofHours(3));
        sut.recordAnswer();
        report();

        verifyNoInteractions(notificationPublisher);
    }

    //a restart or a firmware update of the device has to pass unnoticed
    @Test
    void should_publish_nothing_before_the_device_has_been_silent_for_the_limit() {
        sut.recordFailure();
        report();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        sut.recordFailure();
        report();

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void should_alert_once_when_the_device_has_been_silent_for_the_limit() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        failFor(Duration.ofMinutes(5));
        report();
        //the passes that follow, one a minute
        failFor(Duration.ofMinutes(1));
        report();
        failFor(Duration.ofMinutes(1));
        report();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verifyNoMoreInteractions(notificationPublisher);
    }

    //one answer in between and the count starts again: only an unbroken silence is an outage
    @Test
    void should_count_the_silence_from_the_first_failure_after_the_last_answer() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());

        failFor(Duration.ofMinutes(4));
        sut.recordAnswer();
        failFor(Duration.ofMinutes(4));
        report();

        verifyNoInteractions(notificationPublisher);

        failFor(Duration.ofMinutes(1));
        report();

        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has not answered since"
            + " 2026-10-06 22:04 (5 min). The furnace and the pumps are not being controlled.");
    }

    @Test
    void should_remind_every_interval_while_the_device_stays_silent() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishReminder(anyString())).thenReturn(Mono.empty());

        failFor(Duration.ofMinutes(5));
        report();
        failFor(Duration.ofMinutes(59));
        report();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verifyNoMoreInteractions(notificationPublisher);

        failFor(Duration.ofMinutes(1));
        report();
        failFor(Duration.ofMinutes(30));
        report();
        failFor(Duration.ofMinutes(30));
        report();

        verify(notificationPublisher).publishReminder("The Shelly in the boiler room is still not answering -"
            + " since 2026-10-06 22:00 (1 h 5 min). The furnace and the pumps are not being controlled.");
        verify(notificationPublisher).publishReminder("The Shelly in the boiler room is still not answering -"
            + " since 2026-10-06 22:00 (2 h 5 min). The furnace and the pumps are not being controlled.");
        verifyNoMoreInteractions(notificationPublisher);
    }

    @Test
    void should_tell_once_when_a_reported_device_answers_again() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        failFor(Duration.ofMinutes(5));
        report();
        failFor(Duration.ofMinutes(20));
        sut.recordAnswer();
        report();
        clock.advance(Duration.ofMinutes(1));
        sut.recordAnswer();
        report();

        verify(notificationPublisher).publishInfo("The Shelly in the boiler room answers again."
            + " It was silent from 2026-10-06 22:00 to 2026-10-06 22:25 (25 min).");
        verify(notificationPublisher, times(1)).publishAlert(anyString());
        verifyNoMoreInteractions(notificationPublisher);
    }

    //nobody was told it was gone, so nobody is told it is back
    @Test
    void should_not_announce_the_return_of_a_device_that_was_never_reported() {
        failFor(Duration.ofMinutes(3));
        sut.recordAnswer();
        report();

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void should_alert_again_about_a_new_outage_after_a_return() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString())).thenReturn(Mono.empty());

        failFor(Duration.ofMinutes(5));
        report();
        sut.recordAnswer();
        report();
        failFor(Duration.ofMinutes(5));
        report();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has not answered since"
            + " 2026-10-06 22:05 (5 min). The furnace and the pumps are not being controlled.");
    }

    //the pass this follows must not fail, and a message the broker did not take is not "sent":
    //the next pass tries the same alert again, as an alert, not as a reminder an hour later
    @Test
    void should_complete_and_try_again_with_the_next_pass_when_the_alert_cannot_be_published() {
        final ListAppender<ILoggingEvent> logs = logs();
        when(notificationPublisher.publishAlert(anyString()))
            .thenReturn(Mono.error(new BoilerException("Notification refused by the broker: no exchange")))
            .thenReturn(Mono.empty());

        failFor(Duration.ofMinutes(5));
        report();
        failFor(Duration.ofMinutes(1));
        report();
        failFor(Duration.ofMinutes(1));
        report();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
        verify(notificationPublisher).publishAlert("The Shelly in the boiler room has not answered since"
            + " 2026-10-06 22:00 (6 min). The furnace and the pumps are not being controlled.");
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

        failFor(Duration.ofMinutes(5));

        sut.report().as(StepVerifier::create).verifyComplete();
    }

    @Test
    void should_try_the_return_again_when_it_cannot_be_published() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        when(notificationPublisher.publishInfo(anyString()))
            .thenReturn(Mono.error(new BoilerException("Notification was not routed to any queue: NO_ROUTE")))
            .thenReturn(Mono.empty());

        failFor(Duration.ofMinutes(5));
        report();
        sut.recordAnswer();
        report();
        clock.advance(Duration.ofMinutes(1));
        report();
        clock.advance(Duration.ofMinutes(1));
        report();

        verify(notificationPublisher, times(2)).publishInfo(anyString());
    }

    //the reactive @Scheduled method calls report() once and subscribes to the same Mono for every
    //run - a time or a state read while the Mono is built would be the one of the pod's start
    @Test
    void should_read_the_time_and_the_state_at_every_subscription_of_the_same_mono() {
        when(notificationPublisher.publishAlert(anyString())).thenReturn(Mono.empty());
        final Mono<Void> scheduled = sut.report();

        scheduled.as(StepVerifier::create).verifyComplete();
        verifyNoInteractions(notificationPublisher);

        failFor(Duration.ofMinutes(5));
        scheduled.as(StepVerifier::create).verifyComplete();

        verify(notificationPublisher).publishAlert(ALERT_AFTER_5_MIN);
    }

    private void report() {
        sut.report().as(StepVerifier::create).verifyComplete();
    }

    //a failed call now and another after the given time, as two passes that far apart would leave it
    private void failFor(final Duration duration) {
        sut.recordFailure();
        clock.advance(duration);
        sut.recordFailure();
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
