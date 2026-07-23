package fr.abes.bestppn.service;

import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.dto.wscall.NoticeSummaryDto;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
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

/**
 * Orchestre la recherche, la notation et la sélection du meilleur PPN pour
 * une ligne KBART.
 *
 * <p>Les candidats sont collectés à partir des identifiants en ligne et
 * imprimé, du DOI, puis de {@code dat2ppn} en dernier recours pour les
 * monographies. Les PPN électroniques reçoivent les scores historiques. Si
 * plusieurs candidats partagent le score maximal, le service délègue leur
 * départage à {@link BestPpnTieBreakerService}.</p>
 */
@Service
@Getter
@Slf4j
public class BestPpnService {
    /**
     * Client des webservices Sudoc utilisés pour rechercher les candidats.
     */
    private final WsService service;

    /**
     * Poids attribué aux PPN électroniques trouvés par l'identifiant en ligne.
     */
    @Value("${score.online.id.to.ppn.elect}")
    private int scoreOnlineId2PpnElect;

    /**
     * Poids attribué aux PPN électroniques obtenus par rebond depuis
     * l'identifiant imprimé.
     */
    @Value("${score.print.id.to.ppn.elect}")
    private int scorePrintId2PpnElect;

    /**
     * Poids attribué aux PPN électroniques trouvés directement depuis
     * l'identifiant imprimé.
     */
    @Value("${score.error.type.notice}")
    private int scorePrintId2PpnElectNotByRebound;

    /**
     * Poids attribué aux PPN électroniques trouvés à partir du DOI.
     */
    @Value("${score.doi.to.ppn}")
    private int scoreDoi2Ppn;

    /**
     * Poids attribué aux PPN électroniques trouvés par {@code dat2ppn}.
     */
    @Value("${score.dat.to.ppn}")
    private int scoreDat2Ppn;

    /**
     * Moteur SOA-501 chargé de départager les meilleurs scores ex aequo.
     */
    private final BestPpnTieBreakerService tieBreakerService;

    /**
     * Producteur Kafka conservé dans le contrat d'injection historique du
     * service et exposé par le getter Lombok.
     */
    private final TopicProducer topicProducer;

    /**
     * Vérifie qu'une URL KBART est présente dans une notice candidate.
     */
    private final CheckUrlService checkUrlService;

    /**
     * Construit l'orchestrateur avec ses services de recherche, de contrôle et
     * d'arbitrage.
     *
     * @param service client des webservices Sudoc
     * @param topicProducer producteur Kafka du traitement bestppn
     * @param checkUrlService service de contrôle des URL dans les notices
     * @param tieBreakerService moteur de départage des PPN ex aequo
     */
    public BestPpnService(
            WsService service,
            TopicProducer topicProducer,
            CheckUrlService checkUrlService,
            BestPpnTieBreakerService tieBreakerService) {
        this.service = service;
        this.topicProducer = topicProducer;
        this.checkUrlService = checkUrlService;
        this.tieBreakerService = tieBreakerService;
    }

    /**
     * Recherche tous les PPN candidats d'une ligne KBART et sélectionne le
     * meilleur résultat.
     *
     * <p>Le fournisseur est ignoré pour les publications en série. Les
     * recherches sont effectuées par identifiant en ligne, identifiant
     * imprimé et DOI. Pour une monographie sans résultat, {@code dat2ppn} sert
     * de dernier recours. Le fournisseur effectivement utilisé est transmis à
     * {@link #getBestPpnByScore(LigneKbartDto, String, Map, Set, boolean)}
     * afin d'alimenter le critère DIFFUSEUR de SOA-501.</p>
     *
     * @param kbart ligne KBART à traiter
     * @param provider code du fournisseur BACON
     * @param isForced {@code true} pour produire un résultat vide plutôt
     * qu'une exception lorsqu'une égalité reste indécidable
     * @return PPN retenu et destination fonctionnelle associée
     * @throws IOException en cas d'échec de lecture d'une notice
     * @throws BestPpnException en cas d'échec métier ou d'égalité non forcée
     * @throws URISyntaxException si une URL candidate est invalide
     * @throws RestClientException en cas d'échec d'un webservice
     * @throws IllegalArgumentException si une donnée d'entrée est invalide
     */
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

        return getBestPpnByScore(
                kbart,
                provider,
                ppnElecScoredList,
                ppnPrintResultList,
                isForced);
    }

    /**
     * Ajoute aux collections de candidats les résultats trouvés à partir de
     * l'identifiant en ligne.
     *
     * @param kbart ligne KBART contenant l'identifiant
     * @param provider fournisseur transmis au webservice
     * @param ppnElecScoredList scores électroniques à compléter
     * @param ppnPrintResultList PPN imprimés à compléter
     * @throws IOException en cas d'échec de lecture associé au contrôle d'URL
     * @throws URISyntaxException si l'URL KBART est invalide
     * @throws IllegalArgumentException si l'appel reçoit une donnée invalide
     * @throws BestPpnException si le webservice Sudoc échoue
     */
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

    /**
     * Ajoute aux collections de candidats les résultats directs et par rebond
     * trouvés à partir de l'identifiant imprimé.
     *
     * @param kbart ligne KBART contenant l'identifiant
     * @param provider fournisseur transmis au webservice
     * @param ppnElecScoredList scores électroniques à compléter
     * @param ppnPrintResultList PPN imprimés à compléter
     * @throws IOException en cas d'échec de lecture associé au contrôle d'URL
     * @throws URISyntaxException si l'URL KBART est invalide
     * @throws IllegalArgumentException si l'appel reçoit une donnée invalide
     * @throws BestPpnException si le webservice Sudoc échoue
     */
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

    /**
     * Recherche des monographies par année, auteur, titre et fournisseur
     * lorsque les autres stratégies n'ont retourné aucun candidat.
     *
     * @param kbart ligne KBART fournissant les critères bibliographiques
     * @param ppnElecScoredList scores électroniques à compléter
     * @param ppnPrintResultList PPN imprimés à compléter
     * @param providerName fournisseur transmis à {@code dat2ppn}
     * @throws IOException en cas d'échec de contrôle d'une notice
     * @throws URISyntaxException si l'URL KBART est invalide
     */
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

    /**
     * Ajoute aux collections de candidats les résultats trouvés à partir du
     * DOI.
     *
     * @param doi DOI normalisé à rechercher
     * @param provider fournisseur transmis au webservice
     * @param ppnElecScoredList scores électroniques à compléter
     * @param ppnPrintResultList PPN imprimés à compléter
     * @throws BestPpnException si le webservice Sudoc échoue
     */
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

    /**
     * Répartit le score d'une réponse Sudoc entre ses PPN électroniques et
     * alimente séparément la collection des PPN imprimés.
     *
     * <p>Une monographie électronique n'est retenue que si le fournisseur ou
     * l'URL KBART est présent dans la notice.</p>
     *
     * @param noticeSummaryDtos notices retournées par le webservice
     * @param titleUrl URL KBART utilisée pour valider une monographie
     * @param score score global associé à la stratégie de recherche
     * @param ppnElecResultList scores électroniques à compléter
     * @param ppnPrintResultList PPN imprimés à compléter
     * @throws URISyntaxException si l'URL KBART est invalide
     * @throws IOException en cas d'échec de contrôle d'une notice
     */
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

    /**
     * Ajoute au candidat sa part du score de la stratégie courante.
     *
     * <p>Si le PPN a déjà été trouvé par une autre stratégie, son nouveau score
     * est cumulé avec le précédent.</p>
     *
     * @param score score global à répartir
     * @param ppnElecScoredList scores électroniques à mettre à jour
     * @param nbPpnElec nombre de PPN électroniques partageant le score
     * @param ppn candidat auquel attribuer la part de score
     */
    private void setScoreToPpnElect(int score, Map<String, Integer> ppnElecScoredList, int nbPpnElec, NoticeSummaryDto ppn) {
        if (!ppnElecScoredList.isEmpty() && ppnElecScoredList.containsKey(ppn.getPpn())) {
            Integer value = ppnElecScoredList.get(ppn.getPpn()) + (score / nbPpnElec);
            ppnElecScoredList.put(ppn.getPpn(), value);
        } else {
            ppnElecScoredList.put(ppn.getPpn(), (score / nbPpnElec));
        }
        log.info(FUNCTIONAL, "PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
        log.debug(TECHNICAL, "PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
    }

    /**
     * Sélectionne le meilleur PPN à partir des scores déjà calculés.
     *
     * <p>Sans candidat électronique, la méthode conserve le traitement
     * historique des PPN imprimés. Un maximum électronique unique est retenu
     * directement. Plusieurs maxima déclenchent le moteur SOA-501 ; son PPN
     * est utilisé uniquement lorsqu'il produit une décision résolue. Une
     * décision indécidable conserve l'exception historique ou, en mode forcé,
     * un PPN vide.</p>
     *
     * @param kbart ligne KBART à laquelle rattacher le résultat ou l'erreur
     * @param provider code fournisseur utilisé par le critère DIFFUSEUR
     * @param ppnElecResultList scores des PPN électroniques candidats
     * @param ppnPrintResultList PPN imprimés candidats
     * @param isForced {@code true} pour retourner un PPN vide en cas
     * d'indécision
     * @return meilleur PPN et destination associée
     * @throws BestPpnException si plusieurs candidats restent possibles hors
     * mode forcé
     */
    public BestPpn getBestPpnByScore(
            LigneKbartDto kbart,
            String provider,
            Map<String, Integer> ppnElecResultList,
            Set<String> ppnPrintResultList,
            boolean isForced) throws BestPpnException {
        Map<String, Integer> ppnElecScore =
                Utils.getMaxValuesFromMap(ppnElecResultList);
        return switch (ppnElecScore.size()) {
            case 0 -> {
                log.info(FUNCTIONAL, "Aucun ppn électronique trouvé. " + kbart);
                yield switch (ppnPrintResultList.size()) {
                    case 0 -> {
                        kbart.setErrorType("Aucun ppn trouvé");
                        yield new BestPpn(
                                null, DESTINATION_TOPIC.NO_PPN_FOUND_SUDOC);
                    }
                    case 1 -> {
                        String printPpn =
                                ppnPrintResultList.stream().toList().getFirst();
                        kbart.setErrorType(
                                "Ppn imprimé trouvé : " + printPpn);
                        log.debug(TECHNICAL, kbart.getErrorType());
                        yield new BestPpn(
                                printPpn,
                                DESTINATION_TOPIC.PRINT_PPN_SUDOC,
                                TYPE_SUPPORT.IMPRIME);
                    }
                    default -> unresolvedPrintTie(
                            kbart, ppnPrintResultList, isForced);
                };
            }
            case 1 -> new BestPpn(
                    ppnElecScore.keySet().stream().findFirst().orElseThrow(),
                    DESTINATION_TOPIC.BEST_PPN_BACON,
                    TYPE_SUPPORT.ELECTRONIQUE);
            default -> {
                TieBreakDecision decision =
                        tieBreakerService.resolve(kbart, provider, ppnElecScore);
                if (decision.resolved()) {
                    yield new BestPpn(
                            decision.selectedPpn(),
                            DESTINATION_TOPIC.BEST_PPN_BACON,
                            TYPE_SUPPORT.ELECTRONIQUE);
                }
                yield unresolvedElectronicTie(
                        kbart, ppnElecScore, isForced);
            }
        };
    }

    /**
     * Produit le résultat historique d'une égalité entre PPN imprimés.
     *
     * @param kbart ligne KBART recevant le message d'erreur
     * @param ppnPrintResultList PPN imprimés ex aequo
     * @param isForced {@code true} pour retourner un PPN vide
     * @return résultat vide destiné au topic bestppn en mode forcé
     * @throws BestPpnException lorsque le traitement n'est pas forcé
     */
    private BestPpn unresolvedPrintTie(
            LigneKbartDto kbart,
            Set<String> ppnPrintResultList,
            boolean isForced) throws BestPpnException {
        String errorString = "Plusieurs ppn imprimés ("
                + String.join(" OU ", ppnPrintResultList)
                + ") ont été trouvés.";
        kbart.setErrorType(errorString);
        if (isForced) {
            log.error(FUNCTIONAL, errorString + " [ " + kbart + " ]");
            return new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
        }
        throw new BestPpnException(errorString + " [ " + kbart + " ] ");
    }

    /**
     * Produit le résultat historique d'une égalité électronique que SOA-501
     * n'a pas pu résoudre.
     *
     * @param kbart ligne KBART recevant le message d'erreur
     * @param ppnElecScore meilleurs scores électroniques ex aequo
     * @param isForced {@code true} pour retourner un PPN vide
     * @return résultat vide destiné au topic bestppn en mode forcé
     * @throws BestPpnException lorsque le traitement n'est pas forcé
     */
    private BestPpn unresolvedElectronicTie(
            LigneKbartDto kbart,
            Map<String, Integer> ppnElecScore,
            boolean isForced) throws BestPpnException {
        String listPpn = String.join(" OU ", ppnElecScore.keySet());
        String errorString = "Plusieurs ppn électroniques ("
                + listPpn
                + ") ont le même score.";
        kbart.setErrorType(errorString);
        if (isForced) {
            log.error(FUNCTIONAL, errorString + " [ " + kbart + " ]");
            return new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
        }
        throw new BestPpnException(errorString + " [ " + kbart + " ]");
    }

}
