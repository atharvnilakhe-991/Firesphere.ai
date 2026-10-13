package ai.firesphere.operations.officer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OfficerOperationsConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock officerOperationsClock() {
        return Clock.systemUTC();
    }
}
