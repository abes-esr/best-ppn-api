package fr.abes.bestppn.model.tiebreak;

import java.util.Comparator;

public record CandidateDecisionKey(
        int currentScore,
        int supportRank,
        int distributorRank,
        int volumeRank,
        int yearRank) implements Comparable<CandidateDecisionKey> {

    private static final Comparator<CandidateDecisionKey> COMPARATOR =
            Comparator.comparingInt(CandidateDecisionKey::currentScore)
                    .thenComparingInt(CandidateDecisionKey::supportRank)
                    .thenComparingInt(CandidateDecisionKey::distributorRank)
                    .thenComparingInt(CandidateDecisionKey::volumeRank)
                    .thenComparingInt(CandidateDecisionKey::yearRank);

    @Override
    public int compareTo(CandidateDecisionKey other) {
        return COMPARATOR.compare(this, other);
    }

    public boolean hasStrongContradiction() {
        return supportRank == 0 || volumeRank == 0 || yearRank == 0;
    }
}
