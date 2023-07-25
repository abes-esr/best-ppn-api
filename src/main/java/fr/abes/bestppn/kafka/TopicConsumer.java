package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.dto.kafka.PpnWithDestinationDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.utils.Utils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicConsumer {
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private BestPpnService service;

    @Autowired
    private TopicProducer producer;

    @Resource(name="kafkaTransactionManager")
    private KafkaTransactionManager kafkaTransactionManager;

    private final List<LigneKbartDto> kbartToSend = new ArrayList<>();

    private final List<PpnKbartProviderDto> ppnToCreate = new ArrayList<>();

    boolean isOnError = false;

    @KafkaListener(topics = {"TEST.TRANSACTION.bacon.kbart.toload"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        String filename = "";
        try {
            if(lignesKbart.value().equals("OK") ){
                if( !isOnError ) {
                    producer.sendKbart(kbartToSend, lignesKbart.headers());
                    producer.sendPrintNotice(ppnToCreate, lignesKbart.headers());
                } else {
                    isOnError = false;
                }
                kbartToSend.clear();
                ppnToCreate.clear();
            } else {
                for (Header header : lignesKbart.headers().toArray()) {
                    if(header.key().equals("FileName")){
                        filename = new String(header.value());
                        break;
                    }
                }
                String provider = Utils.extractProvider(filename);
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                if (ligneFromKafka.isBestPpnEmpty()) {
                    PpnWithDestinationDto ppnWithDestinationDto = service.getBestPpn(ligneFromKafka, provider);
                    switch (ppnWithDestinationDto.getDestination()){
                        case BEST_PPN_BACON -> {
                            ligneFromKafka.setBestPpn(ppnWithDestinationDto.getPpn());
                            kbartToSend.add(ligneFromKafka);
                        }
                        case PRINT_PPN_SUDOC -> {
                            ppnToCreate.add(new PpnKbartProviderDto(ppnWithDestinationDto.getPpn(),ligneFromKafka,provider));
                        }
                    }

                } else {
                    kbartToSend.add(ligneFromKafka);
                }

            }
        } catch (IllegalProviderException e) {
            isOnError = true;
            log.error("Erreur dans les données en entrée, provider incorrect");
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException | RestClientException | IllegalArgumentException e) {
            isOnError = true;
            log.error(e.getMessage());
        }
    }
}
