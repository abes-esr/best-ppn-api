package fr.abes.bestppn.model.tiebreak;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Représente les informations normalisées extraites d'une notice Sudoc pour
 * l'arbitrage SOA-501.
 *
 * <p>Ce modèle ne contient aucune comparaison avec la ligne KBART. Il décrit
 * uniquement ce qui a pu être lu dans la notice XML. Toutes les collections
 * sont copiées défensivement afin que les données d'un candidat restent
 * immuables pendant l'arbitrage.</p>
 *
 * @param ppn identifiant PPN du candidat
 * @param accessMode mode d'accès déduit des zones descriptives du support
 * @param distributors valeurs normalisées des sous-zones {@code 214 #2$c}
 * @param volumeNumbers numéros de volume normalisés extraits des
 * sous-zones {@code 200$h}
 * @param partTitles titres de partie normalisés extraits des
 * sous-zones {@code 200$i}
 * @param yearsFrom100 années extraites de la date 1 codée dans {@code 100$a}
 * @param yearsFrom214 années extraites des sous-zones {@code 214$d}
 * @param evidence valeurs XML brutes ayant servi à construire les métadonnées,
 * destinées à la journalisation
 * @param unreadable indique que la notice est absente, supprimée ou illisible
 */
public record CandidateMetadata(
        String ppn,
        AccessMode accessMode,
        Set<String> distributors,
        Set<String> volumeNumbers,
        Set<String> partTitles,
        Set<Integer> yearsFrom100,
        Set<Integer> yearsFrom214,
        List<String> evidence,
        boolean unreadable) {

    /**
     * Construit des métadonnées immuables.
     *
     * <p>Une collection {@code null} est remplacée par une collection vide.
     * Les autres collections sont copiées pour empêcher toute modification
     * après la construction.</p>
     */
    public CandidateMetadata {
        distributors = immutableSet(distributors);
        volumeNumbers = immutableSet(volumeNumbers);
        partTitles = immutableSet(partTitles);
        yearsFrom100 = immutableSet(yearsFrom100);
        yearsFrom214 = immutableSet(yearsFrom214);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /**
     * Construit la représentation neutre d'une notice qui ne peut pas être
     * exploitée.
     *
     * <p>Les rangs seront calculés à partir de valeurs inconnues et le service
     * d'arbitrage conservera l'égalité au lieu de sélectionner
     * accidentellement un autre candidat.</p>
     *
     * @param ppn identifiant du candidat concerné
     * @param reason raison technique ou fonctionnelle de l'absence de données
     * @return métadonnées vides marquées comme illisibles
     */
    public static CandidateMetadata unreadable(String ppn, String reason) {
        return new CandidateMetadata(
                ppn,
                AccessMode.UNKNOWN,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(reason),
                true);
    }

    /**
     * Regroupe les années provenant des zones 100 et 214.
     *
     * @return ensemble immuable de toutes les années Sudoc du candidat
     */
    public Set<Integer> allYears() {
        Set<Integer> years = new LinkedHashSet<>(yearsFrom100);
        years.addAll(yearsFrom214);
        return Collections.unmodifiableSet(years);
    }

    /**
     * Détecte une incohérence interne entre les dates de la notice.
     *
     * <p>Plus d'une année distincte rend le critère ANNÉE neutre : le service
     * ne peut pas déterminer laquelle constitue la date de référence.</p>
     *
     * @return {@code true} lorsque plusieurs années distinctes ont été
     * extraites
     */
    public boolean hasContradictoryYears() {
        return allYears().size() > 1;
    }

    /**
     * Transforme un ensemble éventuellement nul en copie immuable.
     *
     * @param values valeurs à protéger
     * @param <T> type des valeurs
     * @return ensemble vide si l'entrée est nulle, copie immuable sinon
     */
    private static <T> Set<T> immutableSet(Set<T> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
