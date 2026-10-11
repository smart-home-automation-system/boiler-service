package cloud.cholewa.boiler.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cloud.cholewa.boiler.client.ShellyCall;
import cloud.cholewa.boiler.config.ShellyMonitorProperties;
import cloud.cholewa.commons.notification.NotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The monitor with the publisher of cholewa-commons as it is - only the broker is mocked. The
 * publisher is not this service's code any more, so what the monitor relies on is checked where
 * the two meet: what a notification of this service looks like on the wire, that every way a
 * publish can fail ends the report without an error and is tried again, and that the state moves
 * on although the publisher hands the outcome over to another thread.
 */
@ExtendWith(MockitoExtension.class)
class ShellyAvailabilityMonitorPublishingTest {

    private static final String NOT_PUBLISHED =
        "Notification about the boiler room Shelly not published, it is tried again with the next pass: ";
    private static final String ALERT = "The Shelly in the boiler room has been failing calls since"
        + " 2026-10-06 22:00 (5 min). The furnace and the pumps may not be controlled.";

    @Mock
    private RabbitOperations rabbitOperations;
    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    //22:00 in Warsaw
    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-06T20:00:00Z"));

    private ShellyAvailabilityMonitor sut;

    @BeforeEach
    void setUp() {
        sut = new ShellyAvailabilityMonitor(
            new NotificationPublisher(rabbitOperations, "notification", "dev", Duration.ofSeconds(10)),
            new ShellyMonitorProperties(Duration.ofMinutes(5), Duration.ofHours(1)),
            clock
        );
    }

    //the wire contract with notification-service: the exchange, no routing key, the two headers
    //the exchange routes by, the level and a plain text
    @Test
    void should_publish_the_alert_as_plain_text_routed_by_headers() {
        brokerAnswers(confirmedFromTheConnectionThread());

        alertIsDue();
        report();

        verify(rabbitOperations).send(eq("notification"), eq(""), messageCaptor.capture(), any(CorrelationData.class));
        assertOnTheWire(messageCaptor.getValue(), "alert", "error", ALERT);
    }

    //the same queue as the alert, another color on Discord
    @Test
    void should_publish_the_reminder_as_an_alert_with_the_warn_level() {
        brokerAnswers(confirmedFromTheConnectionThread());

        alertIsDue();
        report();
        clock.advance(Duration.ofHours(1));
        sut.recordFailure(ShellyCall.STATUS);
        report();

        verify(rabbitOperations, times(2))
            .send(eq("notification"), eq(""), messageCaptor.capture(), any(CorrelationData.class));
        assertOnTheWire(messageCaptor.getValue(), "alert", "warn", "The Shelly in the boiler room is still failing"
            + " calls - since 2026-10-06 22:00 (1 h 5 min). The furnace and the pumps may not be controlled.");
    }

    //the news of the return goes to the other queue
    @Test
    void should_publish_the_recovery_with_the_info_category() {
        brokerAnswers(confirmedFromTheConnectionThread());

        alertIsDue();
        report();
        clock.advance(Duration.ofMinutes(1));
        sut.recordAnswer(ShellyCall.STATUS);
        report();

        verify(rabbitOperations, times(2))
            .send(eq("notification"), eq(""), messageCaptor.capture(), any(CorrelationData.class));
        assertOnTheWire(messageCaptor.getValue(), "info", "info", "The Shelly in the boiler room works again."
            + " Its calls failed from 2026-10-06 22:00 to 2026-10-06 22:06 (6 min).");
    }

    private static void assertOnTheWire(
        final Message message, final String category, final String level, final String text
    ) {
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(text);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("text/plain");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        assertThat(message.getMessageProperties().getHeaders())
            .containsEntry("category", category)
            .containsEntry("level", level)
            .containsEntry("env", "dev");
    }

    //the publisher continues on another thread than the one the report was subscribed on: the
    //alert still counts as sent once the report has completed, and the next pass sends no second one
    @Test
    void should_record_the_alert_as_sent_when_the_broker_confirms_it_on_its_own_thread() {
        brokerAnswers(confirmedFromTheConnectionThread());

        alertIsDue();
        report();
        clock.advance(Duration.ofMinutes(1));
        sut.recordFailure(ShellyCall.STATUS);
        report();

        verify(rabbitOperations, times(1))
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void should_complete_and_try_again_when_the_broker_refuses_the_alert() {
        brokerAnswers(correlationData ->
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "exchange not found")));

        assertNotPublishedAndTriedAgain("NotificationPublishException: Notification refused by the broker:"
            + " exchange not found");
    }

    //the broker confirms an unroutable message as well - only the return says it reached no queue
    @Test
    void should_complete_and_try_again_when_the_alert_matches_no_queue() {
        brokerAnswers(correlationData -> {
            correlationData.setReturned(new ReturnedMessage(
                new Message(new byte[0]), 312, "NO_ROUTE", "notification", ""));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        assertNotPublishedAndTriedAgain("NotificationPublishException: Notification was not routed to any queue:"
            + " NO_ROUTE");
    }

    @Test
    void should_complete_and_try_again_when_the_broker_is_unreachable() {
        doThrow(new AmqpConnectException(new RuntimeException("connection refused")))
            .when(rabbitOperations).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertNotPublishedAndTriedAgain("AmqpConnectException: java.lang.RuntimeException: connection refused");
    }

    //a broker that takes the message and does not confirm it: the report ends with the limit of
    //the publisher, well before its own, and the alert is not recorded as sent
    @Test
    void should_complete_after_the_confirm_timeout_when_the_broker_never_confirms() {
        final ListAppender<ILoggingEvent> logs = logs();

        alertIsDue();
        StepVerifier.withVirtualTime(() -> sut.report())
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(9))
            .thenAwait(Duration.ofSeconds(1))
            .verifyComplete();

        assertThat(errors(logs))
            .containsExactly(NOT_PUBLISHED + "NotificationPublishException: Notification not confirmed by the broker"
                + " within PT10S");

        clock.advance(Duration.ofMinutes(1));
        sut.recordFailure(ShellyCall.STATUS);
        brokerAnswers(confirmedFromTheConnectionThread());
        report();

        verify(rabbitOperations, times(2))
            .send(anyString(), anyString(), messageCaptor.capture(), any(CorrelationData.class));
        assertThat(messageCaptor.getValue().getMessageProperties().getHeaders()).containsEntry("level", "error");
    }

    //the failed alert goes out again with the next pass as an alert - red, not a yellow reminder
    private void assertNotPublishedAndTriedAgain(final String failure) {
        final ListAppender<ILoggingEvent> logs = logs();

        alertIsDue();
        report();
        clock.advance(Duration.ofMinutes(1));
        sut.recordFailure(ShellyCall.STATUS);
        report();

        assertThat(errors(logs)).containsExactly(NOT_PUBLISHED + failure, NOT_PUBLISHED + failure);
        verify(rabbitOperations, times(2))
            .send(anyString(), anyString(), messageCaptor.capture(), any(CorrelationData.class));
        assertThat(messageCaptor.getAllValues())
            .allSatisfy(message -> assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("category", "alert")
                .containsEntry("level", "error"));
    }

    //two failed passes five minutes apart; the report of the second one is left to the test
    private void alertIsDue() {
        sut.recordFailure(ShellyCall.STATUS);
        report();
        clock.advance(Duration.ofMinutes(5));
        sut.recordFailure(ShellyCall.STATUS);
    }

    private void report() {
        sut.report().as(StepVerifier::create).expectComplete().verify(Duration.ofSeconds(5));
    }

    //Spring AMQP completes the future on the thread that reads the frames of the connection,
    //some time after the send returned
    private static Consumer<CorrelationData> confirmedFromTheConnectionThread() {
        return correlationData -> CompletableFuture.runAsync(
            () -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)),
            CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS));
    }

    private void brokerAnswers(final Consumer<CorrelationData> outcome) {
        doAnswer(invocation -> {
            outcome.accept(invocation.getArgument(3));
            return null;
        }).when(rabbitOperations).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static List<String> errors(final ListAppender<ILoggingEvent> logs) {
        return logs.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
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

        private void advance(final Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
