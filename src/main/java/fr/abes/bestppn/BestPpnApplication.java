package fr.abes.bestppn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@EnableAspectJAutoProxy
@SpringBootApplication
public class BestPpnApplication {
	public static void main(String[] args) {
		SpringApplication.run(BestPpnApplication.class, args);
	}
}
