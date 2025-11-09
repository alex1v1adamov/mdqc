package example.config;

import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

  @Bean
  public JtsModule jtsModule() {
    return new JtsModule();
  }

  @Bean
  @Primary
  public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> {
      builder.modulesToInstall(
          jtsModule() // Hibernate поддержка
          );
    };
  }
}
