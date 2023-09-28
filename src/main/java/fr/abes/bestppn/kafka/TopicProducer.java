package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.connect.LigneKbartConnect;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicProducer {

    @Value("${topic.name.target.kbart}")
    private String topicKbart;

    @Value("${topic.name.target.noticeimprime}")
    private String topicNoticeImprimee;

    @Value("${topic.name.target.ppnFromKbart}")
    private String topicKbartPpnToCreate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaProducer<String, LigneKbartConnect> producer;

    private final ObjectMapper mapper;


    @Transactional(transactionManager = "kafkaTransactionManager", rollbackFor = {BestPpnException.class, JsonProcessingException.class})
    public void sendKbart(List<LigneKbartDto> kbart, ProviderPackage provider) throws JsonProcessingException, BestPpnException {
        for (LigneKbartDto ligne : kbart) {
            ligne.setProviderPackagePackage(provider.getProviderPackageId().getPackageName());
            ligne.setProviderPackageDateP(provider.getProviderPackageId().getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderPackageId().getProviderIdtProvider());
            if( ligne.isBestPpnEmpty()){
                throw new BestPpnException("La ligne " + ligne +" n'a pas de BestPpn.");
            }
            sendObject(ligne, topicKbart);
        }
        log.debug("message envoyé vers {}", topicKbart);
    }


    @Transactional(transactionManager = "kafkaTransactionManager")
    public void sendPrintNotice(List<PpnKbartProviderDto> ppnKbartProviderDtoList, ProviderPackage provider) throws JsonProcessingException {
        for (PpnKbartProviderDto ppnToCreate : ppnKbartProviderDtoList) {
            ppnToCreate.getKbart().setProviderPackagePackage(provider.getProviderPackageId().getPackageName());
            ppnToCreate.getKbart().setProviderPackageDateP(provider.getProviderPackageId().getDateP());
            ppnToCreate.getKbart().setProviderPackageIdtProvider(provider.getProviderPackageId().getProviderIdtProvider());
            send(mapper.writeValueAsString(ppnToCreate), topicNoticeImprimee);
        }
        log.debug("message envoyé vers {}", topicNoticeImprimee);
    }

    @Transactional(transactionManager = "kafkaTransactionManager")
    public void sendPpnExNihilo(List<LigneKbartDto> ppnFromKbartToCreate, ProviderPackage provider) throws JsonProcessingException {
        for (LigneKbartDto ligne : ppnFromKbartToCreate) {
            ligne.setProviderPackagePackage(provider.getProviderPackageId().getPackageName());
            ligne.setProviderPackageDateP(provider.getProviderPackageId().getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderPackageId().getProviderIdtProvider());
            send(mapper.writeValueAsString(ligne), topicKbartPpnToCreate);
        }
        log.debug("message envoyé vers {}", topicKbartPpnToCreate);
    }

    private void send(String value, String topic) {
        Message<String> message = MessageBuilder
                .withPayload(value)
                .setHeader(KafkaHeaders.TOPIC, topic).build();
        kafkaTemplate.send(message);
    }

    private void sendObject(LigneKbartDto ligneKbartDto, String topic) {
        LigneKbartConnect ligne = new LigneKbartConnect();
        ligne.setPUBLICATIONTITLE(ligneKbartDto.getPublicationTitle());
        ligne.setPRINTIDENTIFIER(ligneKbartDto.getPrintIdentifier());
        ligne.setONLINEIDENTIFIER(ligneKbartDto.getOnlineIdentifier());
        ligne.setDATEFIRSTISSUEONLINE(Utils.formatDate(ligneKbartDto.getDateFirstIssueOnline(), true));
        ligne.setDATELASTISSUEONLINE(Utils.formatDate(ligneKbartDto.getDateLastIssueOnline(), false));
        ligne.setDATEMONOGRAPHPUBLISHEDPRINT(Utils.formatDate(ligneKbartDto.getDateMonographPublishedPrint(), true));
        ligne.setDATEMONOGRAPHPUBLISHEDONLIN(Utils.formatDate(ligneKbartDto.getDateMonographPublishedOnline(), true));
        ligne.setNUMFIRSTVOLONLINE((ligneKbartDto.getNumFirstVolOnline() != null) ? ligneKbartDto.getNumFirstVolOnline().toString() : "");
        ligne.setNUMFIRSTISSUEONLINE((ligneKbartDto.getNumFirstIssueOnline() != null) ? ligneKbartDto.getNumFirstIssueOnline().toString() : "");
        ligne.setNUMLASTVOLONLINE((ligneKbartDto.getNumLastVolOnline() != null) ? ligneKbartDto.getNumLastVolOnline().toString() : "");
        ligne.setNUMLASTISSUEONLINE((ligneKbartDto.getNumLastIssueOnline() != null) ? ligneKbartDto.getNumLastIssueOnline().toString() : "");
        ligne.setTITLEURL(ligneKbartDto.getTitleUrl());
        ligne.setFIRSTAUTHOR(ligneKbartDto.getFirstAuthor());
        ligne.setTITLEID(ligneKbartDto.getTitleId());
        ligne.setEMBARGOINFO(ligneKbartDto.getEmbargoInfo());
        ligne.setCOVERAGEDEPTH(ligneKbartDto.getCoverageDepth());
        ligne.setNOTES(ligneKbartDto.getNotes());
        ligne.setPUBLISHERNAME(ligneKbartDto.getPublisherName());
        ligne.setPUBLICATIONTYPE(ligneKbartDto.getPublicationType());
        ligne.setMONOGRAPHVOLUME((ligneKbartDto.getMonographVolume() != null) ? ligneKbartDto.getMonographVolume().toString() : "");
        ligne.setMONOGRAPHEDITION(ligneKbartDto.getMonographEdition());
        ligne.setFIRSTEDITOR(ligneKbartDto.getFirstEditor());
        ligne.setPARENTPUBLICATIONTITLEID(ligneKbartDto.getParentPublicationTitleId());
        ligne.setPRECEDINGPUBLICATIONTITLEID(ligneKbartDto.getPrecedingPublicationTitleId());
        ligne.setACCESSTYPE(ligneKbartDto.getAccessType());
        ligne.setPROVIDERPACKAGEPACKAGE(ligneKbartDto.getProviderPackagePackage());
        ligne.setPROVIDERPACKAGEDATEP(Utils.convertDateToLocalDate(ligneKbartDto.getProviderPackageDateP()));
        ligne.setPROVIDERPACKAGEIDTPROVIDER(ligneKbartDto.getProviderPackageIdtProvider());
        ligne.setBESTPPN(ligneKbartDto.getBestPpn());

        ProducerRecord<String, LigneKbartConnect> record = new ProducerRecord<>(topic, ligne);
        producer.send(record, (recordMetadata, e) -> {
            if (e == null) {
                log.debug("Envoi à Kafka " + recordMetadata);
            }
            else {
                log.error(e.getMessage());
            }
        });
    }

}
