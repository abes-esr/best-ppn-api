package fr.abes.bestppn.model.tiebreak;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    public CandidateMetadata {
        distributors = immutableSet(distributors);
        volumeNumbers = immutableSet(volumeNumbers);
        partTitles = immutableSet(partTitles);
        yearsFrom100 = immutableSet(yearsFrom100);
        yearsFrom214 = immutableSet(yearsFrom214);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

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

    public Set<Integer> allYears() {
        Set<Integer> years = new LinkedHashSet<>(yearsFrom100);
        years.addAll(yearsFrom214);
        return Collections.unmodifiableSet(years);
    }

    public boolean hasContradictoryYears() {
        return allYears().size() > 1;
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
