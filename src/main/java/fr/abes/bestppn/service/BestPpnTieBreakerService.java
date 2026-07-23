package fr.abes.bestppn.service;

import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.tiebreak.CandidateDecisionKey;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import fr.abes.bestppn.utils.PUBLICATION_TYPE;
import fr.abes.bestppn.utils.TieBreakNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;

import static fr.abes.bestppn.utils.LogMarkers.FUNCTIONAL;
import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

/**
 * Départage les PPN restés ex aequo après le calcul de score historique de
 * bestppn.
 *
 * <p>Le service charge une seule fois la notice XML de chaque candidat, en
 * extrait les métadonnées utiles, puis construit une clé comparée dans l'ordre
 * strict suivant : score existant, SUPPORT, DIFFUSEUR, VOLUME et ANNÉE. Pour
 * les publications en série, seuls le score et le support participent
 * réellement à l'arbitrage ; les trois derniers critères reçoivent un rang
 * neutre.</p>
 *
 * <p>Une égalité parfaite, une notice illisible ou une contradiction forte
 * sur le meilleur candidat conserve l'indécision initiale. Les contradictions
 * fortes sont un support physique, un volume incompatible ou une année
 * incompatible. Une divergence de diffuseur n'est pas un veto.</p>
 */
@Service
@Slf4j
public class BestPpnTieBreakerService {
    /**
     * Accès aux notices XML de la base Sudoc.
     */
    private final NoticeService noticeService;

    /**
     * Accès au référentiel BACON utilisé pour relier le code fournisseur
     * KBART à son libellé de diffuseur.
     */
    private final ProviderService providerService;

    /**
     * Convertit une notice XML en métadonnées normalisées utilisables par les
     * critères SUPPORT, DIFFUSEUR, VOLUME et ANNÉE.
     */
    private final CandidateMetadataExtractor extractor;

    /**
     * Construit le service d'arbitrage avec ses trois sources de données.
     *
     * @param noticeService service de lecture des notices XML
     * @param providerService service de consultation des fournisseurs BACON
     * @param extractor extracteur des métadonnées d'une notice candidate
     */
    public BestPpnTieBreakerService(
            NoticeService noticeService,
            ProviderService providerService,
            CandidateMetadataExtractor extractor) {
        this.noticeService = noticeService;
        this.providerService = providerService;
        this.extractor = extractor;
    }

    /**
     * Tente de sélectionner un unique PPN parmi des candidats ex aequo.
     *
     * <p>Chaque notice est chargée exactement une fois. Les clés de décision
     * sont ensuite comparées lexicographiquement dans l'ordre
     * {@code SCORE > SUPPORT > DIFFUSEUR > VOLUME > ANNÉE}. La première étape
     * de la cascade ne laissant qu'un candidat détermine le critère décisif
     * enregistré dans la décision et dans les logs.</p>
     *
     * <p>L'arbitrage reste indécidable lorsqu'il y a moins de deux candidats,
     * lorsqu'une notice ne peut pas être lue, lorsque plusieurs candidats
     * possèdent la même meilleure clé ou lorsque le meilleur candidat présente
     * une contradiction forte.</p>
     *
     * @param kbart ligne KBART à rapprocher ; elle doit être non nulle
     * @param providerCode code fournisseur de la ligne KBART, éventuellement
     * absent
     * @param tiedCandidates PPN ex aequo associés à leur score bestppn
     * existant
     * @return décision résolue avec le PPN retenu ou décision indécidable avec
     * son motif
     */
    public TieBreakDecision resolve(
            LigneKbartDto kbart,
            String providerCode,
            Map<String, Integer> tiedCandidates) {
        if (tiedCandidates == null || tiedCandidates.size() < 2) {
            return TieBreakDecision.unresolved(Map.of(), "moins de deux candidats");
        }

        log.info(FUNCTIONAL,
                "Arbitrage SOA-501 : candidats={}, scores={}",
                tiedCandidates.keySet(),
                tiedCandidates);

        Optional<Provider> provider = findProvider(providerCode);
        Map<String, CandidateMetadata> metadataByPpn = loadMetadata(tiedCandidates.keySet());
        log.info(FUNCTIONAL,
                "Arbitrage SOA-501 : KBART provider={}, titre={}, volume={}, annéeEnLigne={}, annéeImprimée={}",
                TieBreakNormalizer.normalizeText(providerCode),
                TieBreakNormalizer.normalizeText(kbart.getPublicationTitle()),
                TieBreakNormalizer.normalizeVolume(kbart.getMonographVolume()).orElse(""),
                TieBreakNormalizer.extractYear(kbart.getDateMonographPublishedOnline())
                        .stream().boxed().findFirst().orElse(null),
                TieBreakNormalizer.extractYear(kbart.getDateMonographPublishedPrint())
                        .stream().boxed().findFirst().orElse(null));
        Map<String, CandidateDecisionKey> keys = buildKeys(
                kbart, provider.orElse(null), tiedCandidates, metadataByPpn);

        keys.forEach((ppn, key) -> log.info(
                FUNCTIONAL,
                "Arbitrage SOA-501 : ppn={}, clé={}, preuves={}",
                ppn,
                key,
                metadataByPpn.get(ppn).evidence()));

        if (metadataByPpn.values().stream().anyMatch(CandidateMetadata::unreadable)) {
            log.warn(FUNCTIONAL,
                    "Arbitrage SOA-501 indécidable : notice absente, supprimée ou illisible");
            return TieBreakDecision.unresolved(
                    keys, "notice absente, supprimée ou illisible");
        }

        CandidateDecisionKey maxKey = keys.values().stream()
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<String> winners = keys.entrySet().stream()
                .filter(entry -> entry.getValue().equals(maxKey))
                .map(Map.Entry::getKey)
                .toList();

        if (winners.size() != 1) {
            log.warn(FUNCTIONAL,
                    "Arbitrage SOA-501 indécidable : clés identiques pour {}",
                    winners);
            return TieBreakDecision.unresolved(keys, "clés identiques");
        }

        String winner = winners.getFirst();
        String decisiveCriterion = logCascadeAndFindDecisiveCriterion(keys);
        if (maxKey.hasStrongContradiction()) {
            log.warn(FUNCTIONAL,
                    "Arbitrage SOA-501 refusé : ppn={}, clé={}, contradiction forte",
                    winner,
                    maxKey);
            return TieBreakDecision.unresolved(
                    keys, "contradiction forte sur le meilleur candidat");
        }

        log.info(FUNCTIONAL,
                "Arbitrage SOA-501 résolu : ppn={}, critère décisif={}",
                winner,
                decisiveCriterion);
        return TieBreakDecision.selected(winner, keys, decisiveCriterion);
    }

    /**
     * Recherche le fournisseur BACON correspondant au code reçu.
     *
     * <p>Un code absent, un fournisseur inconnu ou une erreur d'accès à BACON
     * produit une valeur vide. Le critère DIFFUSEUR sera alors neutre et ne
     * bloquera pas les autres critères.</p>
     *
     * @param providerCode code fournisseur provenant du traitement KBART
     * @return fournisseur trouvé, ou valeur vide si la comparaison n'est pas
     * possible
     */
    private Optional<Provider> findProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return Optional.empty();
        }
        try {
            return providerService.findByProvider(providerCode);
        } catch (RuntimeException exception) {
            log.error(TECHNICAL,
                    "Impossible de charger le provider {} pendant l'arbitrage SOA-501",
                    providerCode,
                    exception);
            return Optional.empty();
        }
    }

    /**
     * Charge et extrait les métadonnées de chaque candidat.
     *
     * <p>Les PPN sont parcourus dans l'ordre alphabétique afin de rendre les
     * clés et les logs déterministes. Chaque PPN est lu une seule fois. Une
     * erreur de lecture XML ou d'extraction est transformée en métadonnée
     * illisible ; {@link #resolve(LigneKbartDto, String, Map)} préservera alors
     * l'égalité au lieu de sélectionner un candidat sur des données
     * incomplètes.</p>
     *
     * @param ppns ensemble des PPN à charger
     * @return métadonnées indexées par PPN, y compris une entrée illisible en
     * cas d'échec
     */
    private Map<String, CandidateMetadata> loadMetadata(Set<String> ppns) {
        Map<String, CandidateMetadata> result = new LinkedHashMap<>();
        ppns.stream().sorted().forEach(ppn -> {
            try {
                NoticeXml notice = noticeService.getNoticeByPpn(ppn);
                result.put(ppn, extractor.extract(ppn, notice));
            } catch (IOException | RuntimeException exception) {
                log.error(TECHNICAL,
                        "Impossible de charger la notice {} pendant l'arbitrage SOA-501",
                        ppn,
                        exception);
                result.put(ppn, CandidateMetadata.unreadable(
                        ppn, "erreur de lecture XML"));
            }
        });
        return result;
    }

    /**
     * Calcule la clé de décision complète de chaque candidat.
     *
     * <p>Le score initial est conservé tel quel. Les quatre rangs suivants sont
     * calculés à partir de la ligne KBART, du fournisseur BACON et des
     * métadonnées XML. L'insertion par PPN trié garantit un résultat
     * déterministe.</p>
     *
     * @param kbart ligne KBART servant de référence
     * @param provider fournisseur BACON, ou {@code null} s'il est inconnu
     * @param tiedCandidates scores actuels indexés par PPN
     * @param metadataByPpn métadonnées extraites indexées par PPN
     * @return clés de décision indexées par PPN
     */
    private Map<String, CandidateDecisionKey> buildKeys(
            LigneKbartDto kbart,
            Provider provider,
            Map<String, Integer> tiedCandidates,
            Map<String, CandidateMetadata> metadataByPpn) {
        Map<String, CandidateDecisionKey> keys = new LinkedHashMap<>();
        tiedCandidates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CandidateMetadata metadata = metadataByPpn.get(entry.getKey());
                    keys.put(entry.getKey(), new CandidateDecisionKey(
                            entry.getValue(),
                            supportRank(metadata),
                            distributorRank(kbart, provider, metadata),
                            volumeRank(kbart, metadata),
                            yearRank(kbart, metadata)));
                });
        return keys;
    }

    /**
     * Attribue le rang SUPPORT d'un candidat.
     *
     * @param metadata métadonnées de la notice candidate
     * @return {@code 2} pour une ressource en ligne, {@code 1} si le support
     * est inconnu et {@code 0} pour un support physique ; ce dernier rang est
     * une contradiction forte
     */
    private int supportRank(CandidateMetadata metadata) {
        return switch (metadata.accessMode()) {
            case ONLINE -> 2;
            case UNKNOWN -> 1;
            case PHYSICAL -> 0;
        };
    }

    /**
     * Attribue le rang DIFFUSEUR d'une monographie.
     *
     * <p>Le code ou le libellé BACON est recherché parmi les diffuseurs de la
     * notice. Une correspondance vaut {@code 2}, l'absence de fournisseur ou
     * de diffuseur vaut le rang neutre {@code 1}, et une divergence explicite
     * vaut {@code 0}. Ce dernier rang reste un indice de départage et n'est pas
     * une contradiction forte, car une URL peut justifier le candidat.</p>
     *
     * @param kbart ligne KBART servant à déterminer le type de publication
     * @param provider fournisseur BACON, ou {@code null}
     * @param metadata métadonnées de la notice candidate
     * @return rang DIFFUSEUR entre 0 et 2, ou {@code 1} pour une publication en
     * série
     */
    private int distributorRank(
            LigneKbartDto kbart,
            Provider provider,
            CandidateMetadata metadata) {
        if (!isMonograph(kbart)) {
            return 1;
        }
        if (provider == null || metadata.distributors().isEmpty()) {
            return 1;
        }

        String displayName = TieBreakNormalizer.normalizeText(provider.getDisplayName());
        boolean match = metadata.distributors().stream().anyMatch(distributor ->
                TieBreakNormalizer.containsWord(distributor, provider.getProvider())
                        || (!displayName.isBlank()
                        && (distributor.equals(displayName)
                        || distributor.contains(displayName))));
        return match ? 2 : 0;
    }

    /**
     * Attribue le rang VOLUME d'une monographie.
     *
     * <p>Le volume attendu provient d'abord de {@code monograph_volume}, puis
     * à défaut du titre KBART. Une correspondance exacte avec un numéro de
     * volume de la notice vaut {@code 3}. Une correspondance du titre KBART
     * avec un titre de partie issu du champ {@code 200$i} vaut {@code 2}.
     * L'absence de volume exploitable vaut le rang neutre {@code 1}. Une
     * divergence explicite vaut {@code 0} et constitue une contradiction
     * forte.</p>
     *
     * @param kbart ligne KBART contenant le volume ou le titre attendu
     * @param metadata métadonnées de la notice candidate
     * @return rang VOLUME entre 0 et 3, ou {@code 1} pour une publication en
     * série
     */
    private int volumeRank(LigneKbartDto kbart, CandidateMetadata metadata) {
        if (!isMonograph(kbart)) {
            return 1;
        }

        Optional<String> expectedVolume =
                TieBreakNormalizer.normalizeVolume(kbart.getMonographVolume());
        if (expectedVolume.isEmpty()) {
            expectedVolume = TieBreakNormalizer.extractVolumeFromTitle(
                    kbart.getPublicationTitle());
        }

        if (expectedVolume.isPresent()
                && metadata.volumeNumbers().contains(expectedVolume.get())) {
            return 3;
        }

        String normalizedTitle =
                TieBreakNormalizer.normalizeText(kbart.getPublicationTitle());
        boolean partTitleMatch = metadata.partTitles().stream()
                .anyMatch(partTitle -> !partTitle.isBlank()
                        && normalizedTitle.contains(partTitle));
        if (partTitleMatch) {
            return 2;
        }

        return expectedVolume.isEmpty() ? 1 : 0;
    }

    /**
     * Attribue le rang ANNÉE d'une monographie.
     *
     * <p>Une correspondance avec l'année de publication en ligne vaut
     * {@code 3} et prévaut sur la correspondance avec l'année imprimée, qui
     * vaut {@code 2}. L'absence d'année KBART, l'absence d'année Sudoc ou des
     * années contradictoires dans la notice donnent le rang neutre {@code 1}.
     * Une divergence explicite vaut {@code 0} et constitue une contradiction
     * forte.</p>
     *
     * @param kbart ligne KBART contenant les dates de publication
     * @param metadata métadonnées et années extraites de la notice candidate
     * @return rang ANNÉE entre 0 et 3, ou {@code 1} pour une publication en
     * série
     */
    private int yearRank(LigneKbartDto kbart, CandidateMetadata metadata) {
        if (!isMonograph(kbart)) {
            return 1;
        }

        OptionalInt onlineYear = TieBreakNormalizer.extractYear(
                kbart.getDateMonographPublishedOnline());
        OptionalInt printYear = TieBreakNormalizer.extractYear(
                kbart.getDateMonographPublishedPrint());
        if (onlineYear.isEmpty() && printYear.isEmpty()) {
            return 1;
        }

        Set<Integer> sudocYears = metadata.allYears();
        if (sudocYears.isEmpty() || metadata.hasContradictoryYears()) {
            return 1;
        }
        if (onlineYear.isPresent() && sudocYears.contains(onlineYear.getAsInt())) {
            return 3;
        }
        if (printYear.isPresent() && sudocYears.contains(printYear.getAsInt())) {
            return 2;
        }
        return 0;
    }

    /**
     * Indique si les critères DIFFUSEUR, VOLUME et ANNÉE doivent être
     * appliqués.
     *
     * @param kbart ligne KBART examinée
     * @return {@code true} uniquement pour le type de publication
     * {@link PUBLICATION_TYPE#MONOGRAPH}
     */
    private boolean isMonograph(LigneKbartDto kbart) {
        return kbart != null
                && PUBLICATION_TYPE.MONOGRAPH.name().equalsIgnoreCase(
                kbart.getPublicationType());
    }

    /**
     * Rejoue la cascade de comparaison pour tracer les éliminations et
     * identifier le critère décisif.
     *
     * <p>Cette méthode ne recalcule pas le gagnant : elle parcourt les clés
     * déjà établies et élimine, critère par critère, les candidats dont le rang
     * est inférieur au maximum parmi les survivants.</p>
     *
     * @param keys clés de décision indexées par PPN
     * @return nom du premier critère laissant un seul candidat, ou
     * {@code AUCUN} si l'égalité subsiste
     */
    private String logCascadeAndFindDecisiveCriterion(
            Map<String, CandidateDecisionKey> keys) {
        List<String> survivors = new ArrayList<>(keys.keySet());
        String decisive = "AUCUN";
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::currentScore, "SCORE");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::supportRank, "SUPPORT");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::distributorRank, "DIFFUSEUR");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::volumeRank, "VOLUME");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        return decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::yearRank, "ANNÉE");
    }

    /**
     * Applique une étape de la cascade aux candidats encore en lice.
     *
     * <p>Les candidats sous le rang maximal sont retirés de la liste mutable
     * des survivants et consignés dans les logs fonctionnels.</p>
     *
     * @param survivors PPN encore en lice ; la liste est modifiée par la
     * méthode
     * @param keys clés de décision indexées par PPN
     * @param rank fonction extrayant le rang du critère courant
     * @param criterion nom fonctionnel du critère pour les logs et la décision
     * @return le nom du critère s'il ne reste qu'un candidat, sinon
     * {@code AUCUN}
     */
    private String decisiveAfterStep(
            List<String> survivors,
            Map<String, CandidateDecisionKey> keys,
            ToIntFunction<CandidateDecisionKey> rank,
            String criterion) {
        int max = survivors.stream()
                .map(keys::get)
                .mapToInt(rank)
                .max()
                .orElseThrow();
        List<String> eliminated = survivors.stream()
                .filter(ppn -> rank.applyAsInt(keys.get(ppn)) < max)
                .toList();
        survivors.removeAll(eliminated);
        if (!eliminated.isEmpty()) {
            log.info(FUNCTIONAL,
                    "Arbitrage SOA-501 : critère={}, éliminés={}, restants={}",
                    criterion,
                    eliminated,
                    survivors);
        }
        return survivors.size() == 1 ? criterion : "AUCUN";
    }
}
