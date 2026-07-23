package fr.abes.bestppn.service;

import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.tiebreak.AccessMode;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BestPpnTieBreakerServiceTest {
    @Mock
    private NoticeService noticeService;
    @Mock
    private ProviderService providerService;
    @Mock
    private CandidateMetadataExtractor extractor;

    private BestPpnTieBreakerService service;

    @BeforeEach
    void setUp() {
        service = new BestPpnTieBreakerService(noticeService, providerService, extractor);
    }

    @Test
    void supportEstPrioritairePourUneSerie() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE));
        candidate("100000002", metadata("100000002", AccessMode.UNKNOWN));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertTrue(decision.resolved());
        assertEquals("100000001", decision.selectedPpn());
        assertEquals("SUPPORT", decision.reason());
    }

    @Test
    void diffuseurDepartageDeuxMonographies() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        Provider provider = provider("CAIRN", "Cairn.info");
        when(providerService.findByProvider("CAIRN")).thenReturn(Optional.of(provider));
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of("cairn info"), Set.of(), Set.of(), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of("autre diffuseur"), Set.of(), Set.of(), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(2, decision.keys().get("100000001").distributorRank());
        assertEquals(0, decision.keys().get("100000002").distributorRank());
    }

    @Test
    void providerAbsentDeBaconResteNeutre() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        when(providerService.findByProvider("INCONNU")).thenReturn(Optional.empty());
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of("premier diffuseur"), Set.of(), Set.of(), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of("second diffuseur"), Set.of(), Set.of(), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "INCONNU", ties());

        assertFalse(decision.resolved());
        assertEquals(1, decision.keys().get("100000001").distributorRank());
        assertEquals(1, decision.keys().get("100000002").distributorRank());
    }

    @Test
    void volumeExactDepartageDeuxMonographies() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setMonographVolume("Tome II");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of("2"), Set.of(), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of("3"), Set.of(), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(3, decision.keys().get("100000001").volumeRank());
        assertEquals(0, decision.keys().get("100000002").volumeRank());
    }

    @Test
    void titreDePartieDepartageSansMonographVolume() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setPublicationTitle("Traité complet - Méthodes numériques");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of(), Set.of("methodes numeriques"), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of(), Set.of("autre partie"), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(2, decision.keys().get("100000001").volumeRank());
        assertEquals(1, decision.keys().get("100000002").volumeRank());
    }

    @Test
    void anneeEnLigneEstPrioritaireSurAnneeImprimee() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setDateMonographPublishedOnline("2021-01-01");
        kbart.setDateMonographPublishedPrint("2020");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of(), Set.of(), Set.of(2021), Set.of(2021)));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of(), Set.of(), Set.of(2020), Set.of(2020)));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(3, decision.keys().get("100000001").yearRank());
        assertEquals(2, decision.keys().get("100000002").yearRank());
    }

    @Test
    void egaliteDeClesResteIndecidable() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertFalse(decision.resolved());
        assertEquals("clés identiques", decision.reason());
    }

    @Test
    void lectureXmlImpossibleConserveLegalite() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        when(noticeService.getNoticeByPpn("100000001")).thenThrow(new IOException("base indisponible"));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertFalse(decision.resolved());
        assertEquals("notice absente, supprimée ou illisible", decision.reason());
        verify(noticeService, times(1)).getNoticeByPpn("100000001");
        verify(noticeService, times(1)).getNoticeByPpn("100000002");
    }

    @Test
    void supportPhysiqueInterditLaSelection() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        Provider provider = provider("CAIRN", "Cairn.info");
        when(providerService.findByProvider("CAIRN")).thenReturn(Optional.of(provider));
        candidate("100000001", metadata("100000001", AccessMode.PHYSICAL, Set.of("cairn info"), Set.of(), Set.of(), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.PHYSICAL, Set.of("autre diffuseur"), Set.of(), Set.of(), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertFalse(decision.resolved());
        assertEquals("contradiction forte sur le meilleur candidat", decision.reason());
    }

    @Test
    void volumeExplicitementIncompatibleInterditLaSelection() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setMonographVolume("2");
        kbart.setDateMonographPublishedOnline("2021");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of("3"), Set.of(), Set.of(2021), Set.of(2021)));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of("4"), Set.of(), Set.of(2020), Set.of(2020)));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertFalse(decision.resolved());
        assertEquals(0, decision.keys().get("100000001").volumeRank());
    }

    @Test
    void anneeExplicitementIncompatibleInterditLaSelection() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setDateMonographPublishedOnline("2021");
        Provider provider = provider("CAIRN", "Cairn.info");
        when(providerService.findByProvider("CAIRN")).thenReturn(Optional.of(provider));
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of("cairn info"), Set.of(), Set.of(), Set.of(2019), Set.of(2019)));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of("autre diffuseur"), Set.of(), Set.of(), Set.of(2018), Set.of(2018)));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertFalse(decision.resolved());
        assertEquals(0, decision.keys().get("100000001").yearRank());
    }

    @Test
    void seriesIgnorentDiffuseurVolumeEtAnnee() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        kbart.setMonographVolume("2");
        kbart.setDateMonographPublishedOnline("2021");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of("cairn info"), Set.of("2"), Set.of(), Set.of(2021), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of("autre"), Set.of("3"), Set.of(), Set.of(2020), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertFalse(decision.resolved());
        assertEquals(1, decision.keys().get("100000001").distributorRank());
        assertEquals(1, decision.keys().get("100000001").volumeRank());
        assertEquals(1, decision.keys().get("100000001").yearRank());
    }

    private void candidate(String ppn, CandidateMetadata metadata) throws IOException {
        NoticeXml notice = new NoticeXml();
        when(noticeService.getNoticeByPpn(ppn)).thenReturn(notice);
        when(extractor.extract(ppn, notice)).thenReturn(metadata);
    }

    private static LigneKbartDto kbart(String publicationType) {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setPublicationType(publicationType);
        kbart.setPublicationTitle("Titre");
        return kbart;
    }

    private static Map<String, Integer> ties() {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("100000002", 10);
        return candidates;
    }

    private static CandidateMetadata metadata(String ppn, AccessMode accessMode) {
        return metadata(ppn, accessMode, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static CandidateMetadata metadata(
            String ppn,
            AccessMode accessMode,
            Set<String> distributors,
            Set<String> volumeNumbers,
            Set<String> partTitles,
            Set<Integer> yearsFrom100,
            Set<Integer> yearsFrom214) {
        return new CandidateMetadata(
                ppn,
                accessMode,
                distributors,
                volumeNumbers,
                partTitles,
                yearsFrom100,
                yearsFrom214,
                List.of(),
                false);
    }

    private static Provider provider(String code, String displayName) {
        Provider provider = new Provider(code);
        provider.setDisplayName(displayName);
        return provider;
    }
}
