package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.basexml.notice.Controlfield;
import fr.abes.bestppn.model.entity.basexml.notice.Datafield;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.entity.basexml.notice.SubField;
import fr.abes.bestppn.model.tiebreak.AccessMode;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import fr.abes.bestppn.utils.TieBreakNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Extrait d'une notice UNIMARC XML les métadonnées nécessaires au départage
 * SOA-501.
 *
 * <p>Ce composant ne compare pas les valeurs de la notice avec la ligne KBART
 * et ne choisit aucun PPN. Il collecte et normalise les preuves utiles aux
 * critères SUPPORT, DIFFUSEUR, VOLUME et ANNÉE, puis construit un
 * {@link CandidateMetadata} immuable.</p>
 *
 * <p>L'extraction est défensive : les listes, zones et sous-zones absentes ou
 * mal formées sont ignorées. Une notice absente ou supprimée est explicitement
 * marquée comme illisible afin que le futur service d'arbitrage conserve
 * l'égalité.</p>
 */
@Component
public class CandidateMetadataExtractor {
    /**
     * Expressions normalisées considérées comme une preuve explicite d'accès
     * en ligne dans les zones 530, 531 ou 215.
     */
    private static final List<String> ONLINE_MARKERS =
            List.of("online", "en ligne", "ressource en ligne", "acces internet");

    /**
     * Expressions normalisées considérées comme une preuve explicite de
     * support matériel dans les zones 530, 531 ou 215.
     */
    private static final List<String> PHYSICAL_MARKERS =
            List.of("cd rom", "cederom", "dvd rom", "disque optique");

    /**
     * Transforme une notice XML en métadonnées normalisées.
     *
     * <p>Les zones traitées sont :</p>
     *
     * <ul>
     *   <li>530$b, 531$b et toutes les sous-zones 215 pour le support ;</li>
     *   <li>214 #2$c pour le diffuseur ;</li>
     *   <li>200$h et 200$i pour le volume ou le titre de partie ;</li>
     *   <li>100$a et 214$d pour les années.</li>
     * </ul>
     *
     * <p>La zone 008 est conservée dans les preuves, mais ne suffit jamais à
     * elle seule à produire le mode {@link AccessMode#ONLINE}.</p>
     *
     * @param ppn identifiant du candidat, utilisé même si la notice est
     * absente
     * @param notice notice UNIMARC désérialisée, éventuellement nulle
     * @return métadonnées extraites, ou métadonnées illisibles si la notice
     * est absente ou supprimée
     */
    public CandidateMetadata extract(String ppn, NoticeXml notice) {
        if (notice == null) {
            return CandidateMetadata.unreadable(ppn, "notice absente");
        }
        if (notice.getLeader() != null
                && notice.getLeader().length() > 5
                && notice.getLeader().charAt(5) == 'd') {
            return CandidateMetadata.unreadable(ppn, "notice supprimée");
        }

        Set<String> supportValues = new LinkedHashSet<>();
        Set<String> distributors = new LinkedHashSet<>();
        Set<String> volumeNumbers = new LinkedHashSet<>();
        Set<String> partTitles = new LinkedHashSet<>();
        Set<Integer> yearsFrom100 = new LinkedHashSet<>();
        Set<Integer> yearsFrom214 = new LinkedHashSet<>();
        List<String> evidence = new ArrayList<>();

        for (Controlfield controlfield : safeControlfields(notice)) {
            if (controlfield != null && Objects.equals("008", controlfield.getTag())) {
                addEvidence(evidence, "008", null, controlfield.getValue());
            }
        }

        for (Datafield field : safeDatafields(notice)) {
            if (field == null || field.getTag() == null) {
                continue;
            }
            switch (field.getTag()) {
                case "530", "531" -> values(field, "b").forEach(value -> {
                    addNormalized(supportValues, value);
                    addEvidence(evidence, field.getTag(), "b", value);
                });
                case "215" -> safeSubfields(field).forEach(subfield -> {
                    addNormalized(supportValues, subfield.getValue());
                    addEvidence(evidence, "215", subfield.getCode(), subfield.getValue());
                });
                case "214" -> extractPublicationField(
                        field, distributors, yearsFrom214, evidence);
                case "200" -> extractTitleField(
                        field, volumeNumbers, partTitles, evidence);
                case "100" -> values(field, "a").forEach(value -> {
                    TieBreakNormalizer.extractDate1From100(value).ifPresent(yearsFrom100::add);
                    addEvidence(evidence, "100", "a", value);
                });
                default -> {
                }
            }
        }

        return new CandidateMetadata(
                ppn,
                accessMode(supportValues),
                distributors,
                volumeNumbers,
                partTitles,
                yearsFrom100,
                yearsFrom214,
                evidence,
                false);
    }

    /**
     * Extrait d'une zone 214 les années de publication et, lorsque le second
     * indicateur vaut 2, les diffuseurs.
     *
     * @param field zone 214 à analyser
     * @param distributors ensemble recevant les valeurs normalisées de 214$c
     * @param years ensemble recevant les années trouvées dans 214$d
     * @param evidence liste recevant les valeurs brutes utilisées
     */
    private void extractPublicationField(
            Datafield field,
            Set<String> distributors,
            Set<Integer> years,
            List<String> evidence) {
        values(field, "d").forEach(value -> {
            years.addAll(TieBreakNormalizer.extractYears(value));
            addEvidence(evidence, "214", "d", value);
        });
        if (Objects.equals("2", field.getInd2())) {
            values(field, "c").forEach(value -> {
                addNormalized(distributors, value);
                addEvidence(evidence, "214", "c", value);
            });
        }
    }

    /**
     * Extrait d'une zone 200 les numéros et titres de partie.
     *
     * <p>Les valeurs 200$h sont converties en clés de volume numériques. Les
     * valeurs 200$i sont seulement normalisées afin de permettre leur
     * comparaison ultérieure avec le titre KBART.</p>
     *
     * @param field zone 200 à analyser
     * @param volumeNumbers ensemble recevant les volumes normalisés
     * @param partTitles ensemble recevant les titres de partie normalisés
     * @param evidence liste recevant les valeurs brutes utilisées
     */
    private void extractTitleField(
            Datafield field,
            Set<String> volumeNumbers,
            Set<String> partTitles,
            List<String> evidence) {
        values(field, "h").forEach(value -> {
            TieBreakNormalizer.normalizeVolume(value).ifPresent(volumeNumbers::add);
            addEvidence(evidence, "200", "h", value);
        });
        values(field, "i").forEach(value -> {
            addNormalized(partTitles, value);
            addEvidence(evidence, "200", "i", value);
        });
    }

    /**
     * Détermine le mode d'accès à partir des mentions descriptives
     * normalisées.
     *
     * @param supportValues mentions extraites de 530$b, 531$b et 215
     * @return {@link AccessMode#ONLINE} pour une preuve en ligne seule,
     * {@link AccessMode#PHYSICAL} pour une preuve physique seule, ou
     * {@link AccessMode#UNKNOWN} si les preuves sont absentes ou
     * contradictoires
     */
    private AccessMode accessMode(Set<String> supportValues) {
        boolean online = containsMarker(supportValues, ONLINE_MARKERS);
        boolean physical = containsMarker(supportValues, PHYSICAL_MARKERS);
        if (online == physical) {
            return AccessMode.UNKNOWN;
        }
        return online ? AccessMode.ONLINE : AccessMode.PHYSICAL;
    }

    /**
     * Vérifie si au moins une valeur contient l'un des marqueurs attendus.
     *
     * @param values valeurs normalisées de la notice
     * @param markers expressions normalisées recherchées
     * @return {@code true} dès qu'une expression est trouvée
     */
    private boolean containsMarker(Set<String> values, List<String> markers) {
        return values.stream().anyMatch(value ->
                markers.stream().anyMatch(value::contains));
    }

    /**
     * Retourne les valeurs non vides d'une sous-zone donnée.
     *
     * @param field zone contenant les sous-zones
     * @param code code de sous-zone recherché
     * @return liste des valeurs brutes correspondantes
     */
    private List<String> values(Datafield field, String code) {
        return safeSubfields(field).stream()
                .filter(subfield -> Objects.equals(code, subfield.getCode()))
                .map(SubField::getValue)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList();
    }

    /**
     * Protège le parcours des zones d'une notice.
     *
     * @param notice notice en cours d'extraction
     * @return liste des zones, ou liste vide si elle est absente
     */
    private List<Datafield> safeDatafields(NoticeXml notice) {
        return notice.getDatafields() == null ? List.of() : notice.getDatafields();
    }

    /**
     * Protège le parcours des zones de contrôle d'une notice.
     *
     * @param notice notice en cours d'extraction
     * @return liste des zones de contrôle, ou liste vide si elle est absente
     */
    private List<Controlfield> safeControlfields(NoticeXml notice) {
        return notice.getControlfields() == null ? List.of() : notice.getControlfields();
    }

    /**
     * Protège le parcours des sous-zones et écarte les éléments nuls issus
     * d'un XML mal formé.
     *
     * @param field zone en cours d'extraction
     * @return liste non nulle de sous-zones exploitables
     */
    private List<SubField> safeSubfields(Datafield field) {
        return field.getSubFields() == null
                ? List.of()
                : field.getSubFields().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Normalise une valeur textuelle avant de l'ajouter à un ensemble.
     *
     * @param target ensemble de destination
     * @param value valeur brute à normaliser
     */
    private void addNormalized(Set<String> target, String value) {
        String normalized = TieBreakNormalizer.normalizeText(value);
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    /**
     * Conserve la provenance et la valeur brute d'une preuve.
     *
     * <p>Le format produit est {@code zone$sous-zone=valeur}, ou
     * {@code zone=valeur} pour une zone de contrôle.</p>
     *
     * @param evidence liste de preuves à enrichir
     * @param tag étiquette UNIMARC
     * @param code code de sous-zone, ou {@code null} pour une zone de contrôle
     * @param value valeur brute
     */
    private void addEvidence(List<String> evidence, String tag, String code, String value) {
        if (value != null && !value.isBlank()) {
            evidence.add(tag + (code == null ? "" : "$" + code) + "=" + value);
        }
    }
}
