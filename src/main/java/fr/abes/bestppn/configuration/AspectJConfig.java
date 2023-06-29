package fr.abes.bestppn.configuration;

import fr.abes.bestppn.utils.ExecutionTimeAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AspectJConfig {
    @Bean
    public ExecutionTimeAspect executionTimeAspect() {
        return new ExecutionTimeAspect();
    }
}
