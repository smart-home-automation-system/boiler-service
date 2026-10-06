package cloud.cholewa.boiler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ShellyMonitorProperties.class)
public class ShellyMonitorConfig {

    //the monitor measures how long the device has been silent; a bean, so a test can move the time
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
