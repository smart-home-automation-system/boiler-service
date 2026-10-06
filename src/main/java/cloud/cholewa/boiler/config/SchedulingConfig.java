package cloud.cholewa.boiler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

//the control pass switches real relays, so a context started by a test must never schedule it -
//ten seconds after the start it would drive the device in the boiler room
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
