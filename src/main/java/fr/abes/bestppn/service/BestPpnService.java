package fr.abes.bestppn.service;

import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.dto.wscall.NoticeSummaryDto;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.utils.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static fr.abes.bestppn.utils.LogMarkers.FUNCTIONAL;
import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

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
    private int scorePrintId2PpnElectNotByRebound;

    @Value("${score.doi.to.ppn}")
    private int scoreDoi2Ppn;

    @Value("${score.dat.to.ppn}")
    private int scoreDat2Ppn;

    private final NoticeService noticeService;

    private final TopicProducer topicProducer;

    private final CheckUrlService checkUrlService;


    public BestPpnService(WsService service, NoticeService noticeService, TopicProducer topicProducer, CheckUrlService checkUrlService) {
        this.service = service;
        this.noticeService = noticeService;
        this.topicProducer = topicProducer;
        this.checkUrlService = checkUrlService;
    }

    public BestPpn getBestPpn(LigneKbartDto kbart, String provider, boolean isForced) throws IOException, BestPpnException, URISyntaxException, RestClientException, IllegalArgumentException {
        Map<String, Integer> ppnElecScoredList = new HashMap<>();
        Set<String> ppnPrintResultList = new HashSet<>();

        if (!kbart.getPublicationType().isEmpty()) {
            provider = kbart.getPublicationType().equals(PUBLICATION_TYPE.SERIAL.toString()) ? "" : provider;
            if (kbart.getOnlineIdentifier() != null && !kbart.getOnlineIdentifier().isEmpty()) {
                log.debug(TECHNICAL, "paramètres en entrée : type : " + kbart.getPublicationType() + " / id : " + kbart.getOnlineIdentifier() + " / provider : " + provider);
                feedPpnListFromOnline(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
            if (kbart.getPrintIdentifier() != null && !kbart.getPrintIdentifier().isEmpty()) {
                log.debug(TECHNICAL, "paramètres en entrée : type : " + kbart.getPublicationType() + " / id : " + kbart.getPrintIdentifier() + " / provider : " + provider);
                feedPpnListFromPrint(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
        }
        String doi = Utils.extractDOI(kbart);
        if (!doi.isBlank()) {
            feedPpnListFromDoi(doi, provider, ppnElecScoredList, ppnPrintResultList);
        }

        if (ppnElecScoredList.isEmpty() && ppnPrintResultList.isEmpty() && kbart.getPublicationType().equals(PUBLICATION_TYPE.MONOGRAPH.toString())) {
            feedPpnListFromDat(kbart, ppnElecScoredList, ppnPrintResultList, provider);
        }

        return getBestPpnByScore(kbart, ppnElecScoredList, ppnPrintResultList, isForced);
    }

    private void feedPpnListFromOnline(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, URISyntaxException, IllegalArgumentException, BestPpnException {
        log.debug(TECHNICAL, "Entrée dans onlineId2Ppn");
        try {
            ResultWsSudocDto result = service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider);
            log.info(FUNCTIONAL, result.toString());
            List<NoticeSummaryDto> noticeElectronique = DtoHandlerService.getNoticeElectronique(result);
            setScoreToEveryPpnFromResultWS(noticeElectronique, kbart.getTitleUrl(), this.scoreOnlineId2PpnElect, ppnElecScoredList, ppnPrintResultList);
        } catch (RestClientException ex) {
            throw new BestPpnException(ex.getMessage());
        }
    }

    private void feedPpnListFromPrint(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, URISyntaxException, IllegalArgumentException, BestPpnException {
        log.debug(TECHNICAL, "Entrée dans printId2Ppn");
        try {
            ResultWsSudocDto resultCallWs = service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider);
            log.info(FUNCTIONAL, resultCallWs.toString());

            List<NoticeSummaryDto> noticeElectroniqueByRebound = DtoHandlerService.getNoticeElectroniqueByRebound(resultCallWs);
            setScoreToEveryPpnFromResultWS(noticeElectroniqueByRebound, kbart.getTitleUrl(), this.scorePrintId2PpnElect, ppnElecScoredList, ppnPrintResultList);

            List<NoticeSummaryDto> noticeElectronique = DtoHandlerService.getNoticeElectronique(resultCallWs);
            setScoreToEveryPpnFromResultWS(noticeElectronique, kbart.getTitleUrl(), this.scorePrintId2PpnElectNotByRebound, ppnElecScoredList, ppnPrintResultList);

            List<NoticeSummaryDto> noticeImprime = DtoHandlerService.getNoticeImprime(resultCallWs);
            setScoreToEveryPpnFromResultWS(noticeImprime, kbart.getTitleUrl(), 0, ppnElecScoredList, ppnPrintResultList);

            // TODO qu'est ce que c'est ?
//            ResultWsSudocDto resultWithTypeAutre = resultCallWs.getNoticeAutre().changeNoticeAutreToElectronique();
//            if (resultWithTypeAutre != null && !resultWithTypeAutre.getPpns().isEmpty()) {
//                setScoreToEveryPpnFromResultWS(resultWithTypeAutre, kbart.getTitleUrl(), 0, ppnElecScoredList, ppnPrintResultList);
//            }
        } catch (RestClientException ex) {
            throw new BestPpnException(ex.getMessage());
        }
    }

    private void feedPpnListFromDat(LigneKbartDto kbart, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList, String providerName) throws IOException, URISyntaxException {
        log.debug(TECHNICAL, "Entrée dans dat2ppn");
        ResultWsSudocDto resultCallWs = new ResultWsSudocDto();
        String dateParameter = "";
        if (!kbart.getAnneeFromDate_monograph_published_online().isEmpty()) {
            log.debug(TECHNICAL, "Appel dat2ppn :  date_monograph_published_online : {} / publication_title : {} auteur : {}", kbart.getDateMonographPublishedOnline(), kbart.getPublicationTitle(), kbart.getAuthor());
            dateParameter = kbart.getAnneeFromDate_monograph_published_online();
        } else if (!kbart.getAnneeFromDate_monograph_published_print().isEmpty()) {
            log.debug(TECHNICAL, "Appel dat2ppn :  date_monograph_published_print : {} / publication_title : {} auteur : {}", kbart.getDateMonographPublishedPrint(), kbart.getPublicationTitle(), kbart.getAuthor());
            dateParameter = kbart.getAnneeFromDate_monograph_published_print();
        }
        if (!dateParameter.isEmpty()) {
            resultCallWs = service.callDat2Ppn(dateParameter, kbart.getAuthor(), kbart.getPublicationTitle(), providerName);
            log.info(FUNCTIONAL, resultCallWs.toString());
        }

        List<NoticeSummaryDto> noticeElectronique = DtoHandlerService.getNoticeElectronique(resultCallWs);
        for (NoticeSummaryDto ppn : noticeElectronique) {
            if (ppn.isProviderPresent() || checkUrlService.checkUrlInNotice(ppn.getPpn(), kbart.getTitleUrl())) {
                setScoreToPpnElect(scoreDat2Ppn, ppnElecScoredList, noticeElectronique.size(), ppn);
            } else {
                log.warn(FUNCTIONAL, "Le PPN {} n'a pas de provider trouvé", ppn);
            }
        }


        List<NoticeSummaryDto> noticeImprime = DtoHandlerService.getNoticeImprime(resultCallWs);
        for (NoticeSummaryDto ppn : noticeImprime){
            ppnPrintResultList.add(ppn.getPpn());
        }
    }

    private void feedPpnListFromDoi(String doi, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws BestPpnException {
        log.debug(TECHNICAL, "Entrée dans doi2ppn");
        ResultWsSudocDto resultWS;
        try {
            resultWS = service.callDoi2Ppn(doi, provider);
            log.info(FUNCTIONAL, resultWS.toString());
            List<NoticeSummaryDto> noticeElectronique = DtoHandlerService.getNoticeElectronique(resultWS);
            int nbPpnElec = noticeElectronique.size();
            for (NoticeSummaryDto noticeSummaryDto : resultWS.getPpns()) {
                if (noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)) {
                    setScoreToPpnElect(scoreDoi2Ppn, ppnElecScoredList, nbPpnElec, noticeSummaryDto);
                } else {
                    log.info(FUNCTIONAL, "PPN Imprimé : {}", noticeSummaryDto);
                    ppnPrintResultList.add(noticeSummaryDto.getPpn());
                }
            }
        } catch (RestClientException e) {
            throw new BestPpnException(e.getMessage());
        }
    }

    private void setScoreToEveryPpnFromResultWS(List<NoticeSummaryDto> noticeSummaryDtos, String titleUrl, int score, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList) throws URISyntaxException, IOException {
        if (!noticeSummaryDtos.isEmpty()) {
            int nbPpnElec = (int) noticeSummaryDtos.stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
            for (NoticeSummaryDto noticeSummaryDto : noticeSummaryDtos) {
                if (noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)) {
                    log.info(FUNCTIONAL, "PPN Imprimé : {}", noticeSummaryDto);
                    ppnPrintResultList.add(noticeSummaryDto.getPpn());
                } else if (noticeSummaryDto.getTypeDocument() != TYPE_DOCUMENT.MONOGRAPHIE || noticeSummaryDto.isProviderPresent() || checkUrlService.checkUrlInNotice(noticeSummaryDto.getPpn(), titleUrl)) {
                    setScoreToPpnElect(score, ppnElecResultList, nbPpnElec, noticeSummaryDto);
                }
            }
        }
    }

    private void setScoreToPpnElect(int score, Map<String, Integer> ppnElecScoredList, int nbPpnElec, NoticeSummaryDto ppn) {
        if (!ppnElecScoredList.isEmpty() && ppnElecScoredList.containsKey(ppn.getPpn())) {
            Integer value = ppnElecScoredList.get(ppn.getPpn()) + (score / nbPpnElec);
            ppnElecScoredList.put(ppn.getPpn(), value);
        } else {
            ppnElecScoredList.put(ppn.getPpn(), (score / nbPpnElec));
        }
        log.info(FUNCTIONAL, "PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
        log.debug( "PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
    }

    public BestPpn getBestPpnByScore(LigneKbartDto kbart, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList, boolean isForced) throws BestPpnException {
        Map<String, Integer> ppnElecScore = Utils.getMaxValuesFromMap(ppnElecResultList);
        return switch (ppnElecScore.size()) {
            case 0 -> {
                log.info(FUNCTIONAL, "Aucun ppn électronique trouvé. " + kbart);
                yield switch (ppnPrintResultList.size()) {
                    case 0 -> {
                        kbart.setErrorType("Aucun ppn trouvé");
                        yield new BestPpn(null, DESTINATION_TOPIC.NO_PPN_FOUND_SUDOC);
                    }

                    case 1 -> {
                        String printPpn = ppnPrintResultList.stream().toList().getFirst();
                        kbart.setErrorType("Ppn imprimé trouvé : " + printPpn);
                        log.debug(TECHNICAL, kbart.getErrorType());
                        yield new BestPpn(printPpn, DESTINATION_TOPIC.PRINT_PPN_SUDOC, TYPE_SUPPORT.IMPRIME);
                    }

                    default -> {
                        String errorString = "Plusieurs ppn imprimés (" + String.join(" OU ", ppnPrintResultList) + ") ont été trouvés.";
                        kbart.setErrorType(errorString);
                        // vérification du forçage
                        if (isForced) {
                            log.error(FUNCTIONAL, errorString + " [ " + kbart + " ]");
                            yield new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
                        } else {
                            throw new BestPpnException(errorString + " [ " + kbart + " ] ");
                        }
                    }
                };
            }
            case 1 ->
                    new BestPpn(ppnElecScore.keySet().stream().findFirst().get(), DESTINATION_TOPIC.BEST_PPN_BACON, TYPE_SUPPORT.ELECTRONIQUE);

            default -> {
                String listPpn = String.join(" OU ", ppnElecScore.keySet());
                String errorString = "Plusieurs ppn électroniques (" + listPpn + ") ont le même score.";
                kbart.setErrorType(errorString);
                // vérification du forçage
                if (isForced) {
                    log.error(FUNCTIONAL, errorString + " [ " + kbart + " ]");
                    yield new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
                } else {
                    throw new BestPpnException(errorString + " [ " + kbart + " ]");
                }
            }
        };
    }

}
