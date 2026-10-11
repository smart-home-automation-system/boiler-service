package cloud.cholewa.boiler;

import cloud.cholewa.boiler.config.ShellyMonitorProperties;
import cloud.cholewa.boiler.service.ShellyAvailabilityMonitor;
import cloud.cholewa.commons.notification.NotificationProperties;
import cloud.cholewa.commons.notification.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

//the profile is set here as well as in surefire: started from an IDE the class would otherwise
//come up with the home profile and the real device address.
//HOSTNAME is set above the environment of the machine, to what Kubernetes puts there for a pod of
//this service, so the name of the connection reads the same on a workstation and in CI
@SpringBootTest(properties = "HOSTNAME=" + BoilerServiceApplicationTest.POD)
@ActiveProfiles("test")
class BoilerServiceApplicationTest {

    static final String POD = "boiler-service-5968757896-bghz5";

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CachingConnectionFactory connectionFactory;
    @Autowired
    private ShellyMonitorProperties shellyMonitorProperties;
    @Autowired
    private NotificationProperties notificationProperties;
    @Autowired
    private ShellyAvailabilityMonitor shellyAvailabilityMonitor;

    @Test
    void contextLoads() {
    }

    @Test
    void should_not_schedule_the_control_pass_in_tests() {
        assertThat(applicationContext.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }

    //the limits the service runs with are those of application.yaml, not the defaults of the
    //record - and they are the owner's numbers
    @Test
    void should_report_the_shelly_after_five_minutes_and_remind_every_hour() {
        assertThat(shellyMonitorProperties.offlineAfter()).isEqualTo(Duration.ofMinutes(5));
        assertThat(shellyMonitorProperties.reminderInterval()).isEqualTo(Duration.ofHours(1));
    }

    //what the notification publisher relies on and only application.yaml provides: without
    //confirms, returns and the mandatory flag a message matching no queue counts as sent, and
    //without the observation the trace of the pass ends at the queue. The publisher of
    //cholewa-commons refuses to start without the first three; the observation and the callback
    //that logs a returned message are checked nowhere else. No getters, hence the reflection
    @Test
    void should_publish_notifications_with_confirms_returns_and_tracing() {
        assertThat(connectionFactory.isPublisherConfirms()).isTrue();
        assertThat(connectionFactory.isPublisherReturns()).isTrue();
        assertThat(connectionFactory.getVirtualHost()).isEqualTo("/notification");
        assertThat(rabbitTemplate.isMandatoryFor(new org.springframework.amqp.core.Message(new byte[0]))).isTrue();
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "observationEnabled")).isEqualTo(true);
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "returnsCallback")).isNotNull();
    }

    //the publisher is the one of cholewa-commons, switched on in application.yaml - there is no
    //copy of it in this service any more - and it publishes over the auto-configured template,
    //the only RabbitOperations of the context
    @Test
    void should_publish_notifications_with_the_publisher_of_the_library() {
        final NotificationPublisher publisher =
            applicationContext.getBean("cholewaNotificationPublisher", NotificationPublisher.class);

        assertThat(applicationContext.getBeansOfType(NotificationPublisher.class)).containsOnlyKeys(
            "cholewaNotificationPublisher");
        assertThat(ReflectionTestUtils.getField(shellyAvailabilityMonitor, "notificationPublisher"))
            .isSameAs(publisher);
        assertThat(applicationContext.getBeansOfType(RabbitOperations.class).values())
            .containsExactly(rabbitTemplate);
        assertThat(ReflectionTestUtils.getField(publisher, "rabbitOperations")).isSameAs(rabbitTemplate);
    }

    //where the notifications go and how long the broker is given: the exchange and the 10 s are
    //also the defaults of the library, pinned here because the monitor was built around them - a
    //confirm later than that is a failed publish, repeated with the next pass. "prod" is what the
    //pod runs with: the home profile has no document of its own, and the manifest sets nothing
    @Test
    void should_publish_notifications_of_production_to_the_notification_exchange() {
        assertThat(notificationProperties.enabled()).isTrue();
        assertThat(notificationProperties.exchange()).isEqualTo("notification");
        assertThat(notificationProperties.env()).isEqualTo("prod");
        assertThat(notificationProperties.confirmTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    //the strategy is the one of cholewa-commons and the connection factory uses it: the broker
    //shows the connection under the name of the pod
    @Test
    void should_name_the_connection_after_the_pod_with_the_strategy_of_the_library() {
        final ConnectionNameStrategy strategy =
            applicationContext.getBean("cholewaPodConnectionNameStrategy", ConnectionNameStrategy.class);

        assertThat(applicationContext.getBeansOfType(ConnectionNameStrategy.class)).containsOnlyKeys(
            "cholewaPodConnectionNameStrategy");
        assertThat(ReflectionTestUtils.getField(connectionFactory, "connectionNameStrategy")).isSameAs(strategy);
        assertThat(strategy.obtainNewConnectionName(connectionFactory)).isEqualTo(POD);
    }
}
