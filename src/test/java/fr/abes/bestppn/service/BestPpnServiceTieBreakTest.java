package fr.abes.bestppn.service;

import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.tiebreak.CandidateDecisionKey;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BestPpnServiceTieBreakTest {
    @Mock
    private WsService wsService;
    @Mock
    private TopicProducer topicProducer;
    @Mock
    private CheckUrlService checkUrlService;
    @Mock
    private BestPpnTieBreakerService tieBreakerService;

    private BestPpnService service;
    private LigneKbartDto kbart;

    @BeforeEach
    void setUp() {
        service = new BestPpnService(
                wsService, topicProducer, checkUrlService, tieBreakerService);
        kbart = new LigneKbartDto();
        kbart.setPublicationTitle("Titre");
        kbart.setPublicationType("monograph");
    }

    @Test
    void utiliseLePpnRetenuPourUneEgaliteMaximale() throws BestPpnException {
        Map<String, Integer> candidates = ties();
        CandidateDecisionKey selectedKey =
                new CandidateDecisionKey(10, 2, 1, 1, 1);
        when(tieBreakerService.resolve(kbart, "CAIRN", candidates))
                .thenReturn(TieBreakDecision.selected(
                        "100000001",
                        Map.of("100000001", selectedKey),
                        "SUPPORT"));

        BestPpn result = service.getBestPpnByScore(
                kbart, "CAIRN", candidates, Set.of(), false);

        assertEquals("100000001", result.getPpn());
        assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
        assertEquals(TYPE_SUPPORT.ELECTRONIQUE, result.getTypeSupport());
    }

    @Test
    void conserveLexceptionActuelleSiLarbitrageEchoue() {
        Map<String, Integer> candidates = ties();
        when(tieBreakerService.resolve(kbart, "CAIRN", candidates))
                .thenReturn(TieBreakDecision.unresolved(
                        Map.of(), "clés identiques"));

        BestPpnException exception = assertThrows(
                BestPpnException.class,
                () -> service.getBestPpnByScore(
                        kbart, "CAIRN", candidates, Set.of(), false));

        assertTrue(exception.getMessage().startsWith(
                "Plusieurs ppn électroniques ("));
        assertTrue(exception.getMessage().contains(
                ") ont le même score."));
    }

    @Test
    void conserveLeBestPpnVideEnModeForceSiLarbitrageEchoue()
            throws BestPpnException {
        Map<String, Integer> candidates = ties();
        when(tieBreakerService.resolve(kbart, "CAIRN", candidates))
                .thenReturn(TieBreakDecision.unresolved(
                        Map.of(), "clés identiques"));

        BestPpn result = service.getBestPpnByScore(
                kbart, "CAIRN", candidates, Set.of(), true);

        assertEquals("", result.getPpn());
        assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    void nappellePasLarbitragePourUnMaximumUnique()
            throws BestPpnException {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("100000002", 5);

        BestPpn result = service.getBestPpnByScore(
                kbart, "CAIRN", candidates, Set.of(), false);

        assertEquals("100000001", result.getPpn());
        verifyNoInteractions(tieBreakerService);
    }

    private static Map<String, Integer> ties() {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("100000002", 10);
        return candidates;
    }
}
