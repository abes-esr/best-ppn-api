package fr.abes.bestppn.configuration;

import fr.abes.LigneKbartConnect;
import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.kafka.KafkaWorkInProgress;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableKafka
public class KafkaConfig {
    @Value("${abes.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${abes.kafka.registry.url}")
    private String registryUrl;


    @Bean
    public ConsumerFactory<String, String> consumerKbartFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG,("SchedulerCoordinator"+ UUID.randomUUID()));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 60000);
        return new DefaultKafkaConsumerFactory<>(props);
    }


    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaKbartListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerKbartFactory());
        return factory;
    }

    @Bean
    public Map<String, Object> producerConfigsWithTransaction() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);
        props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, false);
        return props;
    }

    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }

    @Bean
    public ProducerFactory<String, LigneKbartConnect> producerFactoryLigneKbartConnectWithTransaction() {
        DefaultKafkaProducerFactory<String, LigneKbartConnect> factory = new DefaultKafkaProducerFactory<>(producerConfigsWithTransaction());
        //factory.setTransactionIdPrefix(transactionIdPrefix+"connect-");
        return factory;
    }
    @Bean
    public ProducerFactory<String, LigneKbartImprime> producerFactoryLigneKbartImprimeWithTransaction() {
        DefaultKafkaProducerFactory<String, LigneKbartImprime> factory = new DefaultKafkaProducerFactory<>(producerConfigsWithTransaction());
        //factory.setTransactionIdPrefix(transactionIdPrefix+"print-");
        return factory;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }


    @Bean
    public KafkaTemplate<String, LigneKbartConnect> kafkaTemplateConnect(final ProducerFactory producerFactoryLigneKbartConnectWithTransaction) { return new KafkaTemplate<>(producerFactoryLigneKbartConnectWithTransaction);}

    @Bean
    public KafkaTemplate<String, LigneKbartImprime> kafkaTemplateImprime(final ProducerFactory producerFactoryLigneKbartImprimeWithTransaction) { return new KafkaTemplate<>(producerFactoryLigneKbartImprimeWithTransaction);}

    @Bean
    public KafkaTemplate<String, String> kafkatemplateEndoftraitement(final ProducerFactory producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public Map<String, KafkaWorkInProgress> kafkaWorkInProgress() {
        return new ConcurrentHashMap<>();
    }
}
