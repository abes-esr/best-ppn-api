package fr.abes.bestppn.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPIConfig {

    @Value("${kafkaconvergence.openapi.url}")
    private String url;

    @Bean
    public OpenAPI myOpenAPI() {
        Server server = new Server();
        server.setUrl(url);
        server.setDescription("Server URL in Development environment");

        Contact contact = new Contact();
        contact.setName("Convergence");
        contact.setUrl("https://");

        Info info = new Info()
                .title("Convergence API")
                .version("1.0")
                .contact(contact)
                .description("This API reads a TSV file, calculates the best PPN and sends the answer to Kafka.");


        return new OpenAPI().info(info).servers(List.of(server));
    }
}
