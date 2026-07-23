package fr.abes.bestppn.model.tiebreak;

import java.util.Comparator;

/**
 * Porte les valeurs utilisées pour comparer deux PPN candidats.
 *
 * <p>La comparaison est lexicographique et décroissante selon l'ordre métier
 * suivant : score existant, SUPPORT, DIFFUSEUR, VOLUME, ANNÉE. Un critère de
 * rang inférieur n'est donc consulté que si tous les critères précédents sont
 * égaux.</p>
 *
 * @param currentScore score produit par l'algorithme bestppn existant
 * @param supportRank rang SUPPORT, de 0 à 2
 * @param distributorRank rang DIFFUSEUR, de 0 à 2
 * @param volumeRank rang VOLUME, de 0 à 3
 * @param yearRank rang ANNÉE, de 0 à 3
 */
public record CandidateDecisionKey(
        int currentScore,
        int supportRank,
        int distributorRank,
        int volumeRank,
        int yearRank) implements Comparable<CandidateDecisionKey> {

    /**
     * Comparateur commun qui matérialise l'ordre strict des critères.
     */
    private static final Comparator<CandidateDecisionKey> COMPARATOR =
            Comparator.comparingInt(CandidateDecisionKey::currentScore)
                    .thenComparingInt(CandidateDecisionKey::supportRank)
                    .thenComparingInt(CandidateDecisionKey::distributorRank)
                    .thenComparingInt(CandidateDecisionKey::volumeRank)
                    .thenComparingInt(CandidateDecisionKey::yearRank);

    /**
     * Compare cette clé à une autre en appliquant l'ordre métier.
     *
     * @param other clé du candidat à comparer
     * @return une valeur positive si cette clé est préférable, zéro si les
     * clés sont identiques, une valeur négative sinon
     */
    @Override
    public int compareTo(CandidateDecisionKey other) {
        return COMPARATOR.compare(this, other);
    }

    /**
     * Indique si la clé contient une incompatibilité qui interdit toute
     * sélection automatique.
     *
     * <p>Les contradictions fortes concernent uniquement un support physique,
     * un volume explicitement incompatible ou une année explicitement
     * incompatible. Un rang DIFFUSEUR égal à 0 n'est volontairement pas un
     * veto, car le candidat peut avoir été retenu grâce à son URL.</p>
     *
     * @return {@code true} si le candidat doit être rejeté malgré sa position
     * dans l'ordre lexicographique
     */
    public boolean hasStrongContradiction() {
        return supportRank == 0 || volumeRank == 0 || yearRank == 0;
    }
}
