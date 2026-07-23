package fr.abes.bestppn.model.tiebreak;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CandidateDecisionKeyTest {

    @Test
    void compareDansLOrdreScoreSupportDiffuseurVolumeAnnee() {
        CandidateDecisionKey supportFort = new CandidateDecisionKey(10, 2, 0, 0, 0);
        CandidateDecisionKey diffuseurFort = new CandidateDecisionKey(10, 1, 2, 3, 3);
        CandidateDecisionKey scoreFort = new CandidateDecisionKey(11, 0, 0, 0, 0);

        assertTrue(supportFort.compareTo(diffuseurFort) > 0);
        assertTrue(scoreFort.compareTo(supportFort) > 0);
    }

    @Test
    void identifieUniquementLesContradictionsFortes() {
        assertTrue(new CandidateDecisionKey(10, 0, 2, 3, 3).hasStrongContradiction());
        assertTrue(new CandidateDecisionKey(10, 2, 2, 0, 3).hasStrongContradiction());
        assertTrue(new CandidateDecisionKey(10, 2, 2, 3, 0).hasStrongContradiction());
        assertFalse(new CandidateDecisionKey(10, 2, 0, 3, 3).hasStrongContradiction());
    }

    @Test
    void exposeUneDecisionResolueOuIndecidable() {
        CandidateDecisionKey key = new CandidateDecisionKey(10, 2, 1, 1, 1);
        TieBreakDecision selected = TieBreakDecision.selected("123456789", Map.of("123456789", key), "SUPPORT");
        TieBreakDecision unresolved = TieBreakDecision.unresolved(Map.of("123456789", key), "égalité");

        assertTrue(selected.resolved());
        assertEquals("123456789", selected.selectedPpn());
        assertFalse(unresolved.resolved());
        assertNull(unresolved.selectedPpn());
    }

    @Test
    void detecteDesAnneesSudocContradictoires() {
        CandidateMetadata metadata = new CandidateMetadata(
                "123456789",
                AccessMode.ONLINE,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(2021),
                Set.of(2020, 2021),
                List.of(),
                false);

        assertTrue(metadata.hasContradictoryYears());
    }
}
