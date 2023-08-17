package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.dto.kafka.PpnWithDestinationDto;
import fr.abes.bestppn.dto.mail.PackageKbartDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.service.EmailService;
import fr.abes.bestppn.utils.Utils;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
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

    @Autowired
    private EmailService serviceMail;
    private final List<LigneKbartDto> kbartToSend = new ArrayList<>();

    private final List<PpnKbartProviderDto> ppnToCreate = new ArrayList<>();

    private final PackageKbartDto mailAttachment = new PackageKbartDto();

    boolean isOnError = false;

    @KafkaListener(topics = {"${topic.name.source.kbart}"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        try {
            String filename = "";
            String currentLine = "";
            String totalLine = "";
            for (Header header : lignesKbart.headers().toArray()) {
                if(header.key().equals("FileName")){
                    filename = new String(header.value());
                    ThreadContext.put("package",filename);
                } else if(header.key().equals("CurrentLine")){
                    currentLine = new String(header.value());
                } else if(header.key().equals("TotalLine")){
                    totalLine = new String(header.value());
                }
            }
            String nbLine = currentLine + "/" + totalLine;
            String provider = Utils.extractProvider(filename);

            if(lignesKbart.value().equals("OK") ){
                if( !isOnError ) {
                    producer.sendKbart(kbartToSend, lignesKbart.headers());
                    producer.sendPrintNotice(ppnToCreate, lignesKbart.headers());

                } else {
                    isOnError = false;
                }
                serviceMail.sendMailWithAttachment(filename,mailAttachment);
                kbartToSend.clear();
                ppnToCreate.clear();
                mailAttachment.clearKbartDto();

            } else {
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                if (ligneFromKafka.isBestPpnEmpty()) {
                    log.info("Debut du calcul du bestppn sur la ligne : " + nbLine);
                    PpnWithDestinationDto ppnWithDestinationDto = service.getBestPpn(ligneFromKafka, provider);
                    switch (ppnWithDestinationDto.getDestination()){
                        case BEST_PPN_BACON -> {
                            ligneFromKafka.setBestPpn(ppnWithDestinationDto.getPpn());
                            kbartToSend.add(ligneFromKafka);
                        }
                        case PRINT_PPN_SUDOC -> ppnToCreate.add(new PpnKbartProviderDto(ppnWithDestinationDto.getPpn(),ligneFromKafka,provider));
                    }

                } else {
                    log.info("Bestppn déjà existant sur la ligne : " + nbLine + ", le voici : " + ligneFromKafka.getBestPpn());
                    kbartToSend.add(ligneFromKafka);
                }
                mailAttachment.addKbartDto(ligneFromKafka);
            }
        } catch (IllegalProviderException e) {
            isOnError = true;
            log.error("Erreur dans les données en entrée, provider incorrect");
            addLineToMailAttachementWithErrorMessage(e.getMessage());
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException | RestClientException | IllegalArgumentException e) {
            isOnError = true;
            log.error(e.getMessage());
            addLineToMailAttachementWithErrorMessage(e.getMessage());
        } catch (MessagingException e) {
            isOnError = true;
            log.error(e.getMessage());
            addLineToMailAttachementWithErrorMessage(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void addLineToMailAttachementWithErrorMessage(String messageError) {
        LigneKbartDto ligneVide = new LigneKbartDto();
        ligneVide.setErrorType(messageError);
        mailAttachment.addKbartDto(ligneVide);
    }
}
