package fr.abes.bestppn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.dto.wscall.PpnWithTypeDto;
import fr.abes.bestppn.dto.wscall.ResultDat2PpnWebDto;
import fr.abes.bestppn.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.utils.PUBLICATION_TYPE;
import fr.abes.bestppn.utils.TYPE_DOCUMENT;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import fr.abes.bestppn.utils.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

@Service
@Getter
@Slf4j
public class BestPpnService {
    private final WsService service;
    @Value("${score.online.id.to.ppn.elect}")
    private int scoreOnlineId2PpnElect;

    @Value("${score.print.id.to.ppn.elect}")
    private int scorePrintId2PpnElect;

    @Value("${score.error.type.notice}")
    private int scoreErrorType;

    @Value("${score.doi.to.ppn}")
    private int scoreDoi2Ppn;

    @Value("${score.dat.to.ppn}")
    private int scoreDat2Ppn;

    private final NoticeService noticeService;

    private final TopicProducer topicProducer;

    private final CheckUrlService checkUrlService;

    private final List<PpnKbartProviderDto> ppnToCreate;

    public BestPpnService(WsService service, NoticeService noticeService, TopicProducer topicProducer, CheckUrlService checkUrlService) {
        this.service = service;
        this.noticeService = noticeService;
        this.topicProducer = topicProducer;
        this.checkUrlService = checkUrlService;
        this.ppnToCreate = new ArrayList<>();
    }

    public String getBestPpn(LigneKbartDto kbart, String provider, boolean injectKafka) throws IOException, IllegalPpnException, BestPpnException, URISyntaxException {

        Map<String, Integer> ppnElecScoredList = new HashMap<>();
        Set<String> ppnPrintResultList = new HashSet<>();

        if (!kbart.getPublication_type().isEmpty()) {
            provider = kbart.getPublication_type().equals(PUBLICATION_TYPE.serial.toString()) ? "" : provider;
            if (kbart.getOnline_identifier() != null && !kbart.getOnline_identifier().isEmpty()) {
                log.debug("paramètres en entrée : type : " + kbart.getPublication_type() + " / id : " + kbart.getOnline_identifier() + " / provider : " + provider);
                feedPpnListFromOnline(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
            if (kbart.getPrint_identifier() != null && !kbart.getPrint_identifier().isEmpty()) {
                log.debug("paramètres en entrée : type : " + kbart.getPublication_type() + " / id : " + kbart.getPrint_identifier() + " / provider : " + provider);
                feedPpnListFromPrint(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
        }
        String doi = Utils.extractDOI(kbart);
        if (!doi.isBlank()){
            feedPpnListFromDoi(doi, provider, ppnElecScoredList, ppnPrintResultList);
        }

        if (ppnElecScoredList.isEmpty()) {
            feedPpnListFromDat(kbart, ppnElecScoredList, ppnPrintResultList);
        }

        return getBestPpnByScore(kbart, provider, ppnElecScoredList, ppnPrintResultList, injectKafka);
    }

    private void feedPpnListFromOnline(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, IllegalPpnException, URISyntaxException {
        log.debug("Entrée dans onlineId2Ppn");
        setScoreToEveryPpnFromResultWS(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider), kbart.getTitle_url(), this.scoreOnlineId2PpnElect, ppnElecScoredList, ppnPrintResultList);
    }

    private void feedPpnListFromPrint(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, IllegalPpnException, URISyntaxException {
        log.debug("Entrée dans printId2Ppn");
        ResultWsSudocDto resultCallWs = service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider);
        ResultWsSudocDto resultWithTypeElectronique = resultCallWs.getPpnWithTypeElectronique();
        if (resultWithTypeElectronique != null && !resultWithTypeElectronique.getPpns().isEmpty()) {
            setScoreToEveryPpnFromResultWS(resultWithTypeElectronique, kbart.getTitle_url(), this.scorePrintId2PpnElect, ppnElecScoredList, ppnPrintResultList);
        }
        ResultWsSudocDto resultWithTypeImprime = resultCallWs.getPpnWithTypeImprime();
        if (resultWithTypeElectronique != null && !resultWithTypeImprime.getPpns().isEmpty()) {
            setScoreToEveryPpnFromResultWS(resultWithTypeImprime, kbart.getTitle_url(), this.scoreErrorType, ppnElecScoredList, ppnPrintResultList);
        }
    }

    private void feedPpnListFromDat(LigneKbartDto kbart, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, IllegalPpnException {
        ResultDat2PpnWebDto resultDat2PpnWeb = null;
        if (!kbart.getAnneeFromDate_monograph_published_online().isEmpty()) {
            log.debug("Appel dat2ppn :  date_monograph_published_online : " + kbart.getAnneeFromDate_monograph_published_online() + " / publication_title : " + kbart.getPublication_title() + " auteur : " + kbart.getAuthor());
            resultDat2PpnWeb = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_online(), kbart.getAuthor(), kbart.getPublication_title());
        } else if (ppnElecScoredList.isEmpty() && !kbart.getAnneeFromDate_monograph_published_print().isEmpty()) {
            log.debug("Appel dat2ppn :  date_monograph_published_print : " + kbart.getAnneeFromDate_monograph_published_print() + " / publication_title : " + kbart.getPublication_title() + " auteur : " + kbart.getAuthor());
            resultDat2PpnWeb = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublication_title());
        }
        if(resultDat2PpnWeb != null && !resultDat2PpnWeb.getPpns().isEmpty()) {
            for (String ppn : resultDat2PpnWeb.getPpns()) {
                log.debug("résultat : ppn " + ppn);
                NoticeXml notice = noticeService.getNoticeByPpn(ppn);
                if (notice.isNoticeElectronique()) {
                    ppnElecScoredList.put(ppn, scoreDat2Ppn);
                } else if (notice.isNoticeImprimee()) {
                    ppnPrintResultList.add(ppn);
                }
            }
        }
    }

    private void feedPpnListFromDoi(String doi, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException {
        ResultWsSudocDto resultWS = service.callDoi2Ppn(doi, provider);
        int nbPpnElec = (int) resultWS.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
        for(PpnWithTypeDto ppn : resultWS.getPpns()){
            if(ppn.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)){
                setScoreToPpnElect(scoreDoi2Ppn,ppnElecScoredList,nbPpnElec,ppn);
            } else {
                log.info("PPN Imprimé : " + ppn);
                ppnPrintResultList.add(ppn.getPpn());
            }
        }
    }

    private void setScoreToEveryPpnFromResultWS(ResultWsSudocDto resultCallWs, String titleUrl, int score, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList) throws URISyntaxException, IOException, IllegalPpnException {
        if (!resultCallWs.getPpns().isEmpty()) {
            int nbPpnElec = (int) resultCallWs.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
            for (PpnWithTypeDto ppn : resultCallWs.getPpns()) {
                if(ppn.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)) {
                    log.info("PPN Imprimé : " + ppn);
                    ppnPrintResultList.add(ppn.getPpn());
                } else if (ppn.getTypeDocument() != TYPE_DOCUMENT.MONOGRAPHIE || ppn.isProviderPresent() || checkUrlService.checkUrlInNotice(ppn.getPpn(), titleUrl)){
                    setScoreToPpnElect(score, ppnElecResultList, nbPpnElec, ppn);
                } else {
                    log.error("Le PPN " + ppn + " n'a pas de provider trouvé");
                }
            }
        }
    }

    private void setScoreToPpnElect(int score, Map<String, Integer> ppnElecScoredList, int nbPpnElec, PpnWithTypeDto ppn) {
        if (!ppnElecScoredList.isEmpty() && ppnElecScoredList.containsKey(ppn.getPpn())) {
            Integer value = ppnElecScoredList.get(ppn.getPpn()) + (score / nbPpnElec);
            ppnElecScoredList.put(ppn.getPpn(), value);
        } else {
            ppnElecScoredList.put(ppn.getPpn(), (score / nbPpnElec));
        }
        log.info("PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
    }

    public String getBestPpnByScore(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList, boolean injectKafka) throws BestPpnException, JsonProcessingException {
        Map<String, Integer> ppnElecScore = Utils.getMaxValuesFromMap(ppnElecResultList);
        switch (ppnElecScore.size()) {
            case 0 -> {
                log.info("Aucun ppn électronique trouvé." + kbart);
                switch (ppnPrintResultList.size()) {
                    case 0 -> {
                        log.debug("Envoi kbart et provider vers kafka");
//                         if (injectKafka) topicProducer.sendPrintNotice(null, kbart, provider); //todo
                    }
                    case 1 -> {
                        log.debug("envoi ppn imprimé " + ppnPrintResultList.stream().toList().get(0) + ", kbart et provider");
//                        if (injectKafka) topicProducer.sendPrintNotice(ppnPrintResultList.stream().toList().get(0), kbart, provider); //todo
                    }
                    default -> {
                        kbart.setErrorType("Plusieurs ppn imprimés (" + String.join(", ", ppnPrintResultList) + ") ont été trouvés.");
                        throw new BestPpnException("Plusieurs ppn imprimés (" + String.join(", ", ppnPrintResultList) + ") ont été trouvés.");
                    }
                }
            }
            case 1 -> {
                return ppnElecScore.keySet().stream().findFirst().get();
            }
            default -> {
                String listPpn = String.join(", ", ppnElecScore.keySet());
                String errorString = "Les ppn électroniques " + listPpn + " ont le même score";
                kbart.setErrorType(errorString);
                log.error(errorString);
                throw new BestPpnException(errorString);
            }
        }
        return "";
    }
}
