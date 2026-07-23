package fr.abes.bestppn.model.tiebreak;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TieBreakDecision(
        String selectedPpn,
        Map<String, CandidateDecisionKey> keys,
        String reason) {

    public TieBreakDecision {
        keys = keys == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    public static TieBreakDecision selected(
            String ppn,
            Map<String, CandidateDecisionKey> keys,
            String decisiveCriterion) {
        return new TieBreakDecision(ppn, keys, decisiveCriterion);
    }

    public static TieBreakDecision unresolved(
            Map<String, CandidateDecisionKey> keys,
            String reason) {
        return new TieBreakDecision(null, keys, reason);
    }

    public boolean resolved() {
        return selectedPpn != null;
    }
}
