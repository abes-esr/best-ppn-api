package fr.abes.bestppn.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI OpenAPI() {

        Contact contact = new Contact();
        contact.setName("Abes");
        contact.setUrl("https://github.com/abes-esr/best-ppn-api");
        contact.setEmail("scod@abes.fr");

        Info info = new Info()
                .title("best-ppn-api")
                .version("1.0")
                .contact(contact)
                .description("This read a topic named bacon.kbart.toload which is one line from tsv file. Listener Kafka listens to a topic and retrieves messages as soon as they arrive. Uses services from " +
                        "https://www.sudoc.fr/services/printId2ppn" +
                        "https://www.sudoc.fr/services/onlineId2ppn" +
                        "https://www.sudoc.fr/services/doi2ppn" +
                        "https://www.sudoc.fr/services/dat2ppn" +
                        "to calculate the best ppn from one tsv live, thus, put the result into to topic bacon.kbart.withppn.toload");


        return new OpenAPI().info(info);
    }
}
