package cloud.cholewa.boiler;

import cloud.cholewa.boiler.config.ShellyMonitorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
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
//come up with the home profile and the real device address
@SpringBootTest
@ActiveProfiles("test")
class BoilerServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CachingConnectionFactory connectionFactory;
    @Autowired
    private ShellyMonitorProperties shellyMonitorProperties;

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
    //without the observation the trace of the pass ends at the queue. No getters, hence the
    //reflection
    @Test
    void should_publish_notifications_with_confirms_returns_and_tracing() {
        assertThat(connectionFactory.isPublisherConfirms()).isTrue();
        assertThat(connectionFactory.isPublisherReturns()).isTrue();
        assertThat(connectionFactory.getVirtualHost()).isEqualTo("/notification");
        assertThat(rabbitTemplate.isMandatoryFor(new org.springframework.amqp.core.Message(new byte[0]))).isTrue();
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "observationEnabled")).isEqualTo(true);
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "returnsCallback")).isNotNull();
    }
}
