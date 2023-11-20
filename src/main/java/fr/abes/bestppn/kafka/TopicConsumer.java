package fr.abes.bestppn.kafka;

import fr.abes.bestppn.service.KbartService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TopicConsumer {
    private final KbartService service;

    public TopicConsumer(KbartService service) {
        this.service = service;
    }

    /**
     * Listener Kafka qui écoute un topic et récupère les messages dès qu'ils y arrivent.
     * @param lignesKbart message kafka récupéré par le Consumer Kafka
     */
    @KafkaListener(topics = {"${topic.name.source.kbart}"}, groupId = "${topic.groupid.source.kbart}", containerFactory = "kafkaKbartListenerContainerFactory", concurrency = "${spring.kafka.concurrency.nbThread}")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) throws Exception {
        log.info("Paquet reçu : Partition : " + lignesKbart.partition() + " / offset " + lignesKbart.offset());
        service.processConsumerRecord(lignesKbart);
    }
}
