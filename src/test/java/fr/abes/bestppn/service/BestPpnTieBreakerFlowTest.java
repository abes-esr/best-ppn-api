package fr.abes.bestppn.service;

import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.basexml.notice.Controlfield;
import fr.abes.bestppn.model.entity.basexml.notice.Datafield;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.entity.basexml.notice.SubField;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BestPpnTieBreakerFlowTest {
    @Mock
    private NoticeService noticeService;
    @Mock
    private ProviderService providerService;

    private BestPpnTieBreakerService service;

    @BeforeEach
    void setUp() {
        service = new BestPpnTieBreakerService(
                noticeService,
                providerService,
                new CandidateMetadataExtractor());
    }

    @Test
    void supportEnLigneGagneContreCdRom() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        notices(
                notice("100000001", "Ressource en ligne", null, null, null, null),
                notice("100000002", "CD-ROM", null, null, null, null));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertTrue(decision.resolved());
        assertEquals("100000001", decision.selectedPpn());
        assertEquals("SUPPORT", decision.reason());
    }

    @Test
    void casReel164581340EstClasseCommeCdRom() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        NoticeXml physical = notice(
                "164581340", "( CD-ROM)", null, null, null, null);
        physical.getControlfields().get(1).setValue("Obx3");
        physical.getDatafields().add(
                field("531", "#", "b", "(CD-ROM)"));
        when(noticeService.getNoticeByPpn("100000001")).thenReturn(
                notice("100000001", "Ressource en ligne", null, null, null, null));
        when(noticeService.getNoticeByPpn("164581340")).thenReturn(physical);
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("164581340", 10);

        TieBreakDecision decision = service.resolve(kbart, "", candidates);

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(0, decision.keys().get("164581340").supportRank());
    }

    @Test
    void diffuseur214Indicateur2DepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        Provider provider = new Provider("CAIRN");
        provider.setDisplayName("Cairn.info");
        when(providerService.findByProvider("CAIRN"))
                .thenReturn(Optional.of(provider));
        notices(
                notice("100000001", "Ressource en ligne", "Cairn.info", null, null, null),
                notice("100000002", "Ressource en ligne", "Autre diffuseur", null, null, null));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("DIFFUSEUR", decision.reason());
    }

    @Test
    void volume200hDepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setMonographVolume("Part II");
        notices(
                notice("100000001", "Ressource en ligne", null, "Tome 2", null, null),
                notice("100000002", "Ressource en ligne", null, "Tome 3", null, null));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("VOLUME", decision.reason());
    }

    @Test
    void titreDePartie200iDepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setPublicationTitle("Traité complet - Méthodes numériques");
        notices(
                notice("100000001", "Ressource en ligne", null, null, "Méthodes numériques", null),
                notice("100000002", "Ressource en ligne", null, null, "Autre partie", null));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("VOLUME", decision.reason());
    }

    @Test
    void annee100Et214DepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setDateMonographPublishedOnline("2021-01-01");
        kbart.setDateMonographPublishedPrint("2020");
        notices(
                notice("100000001", "Ressource en ligne", null, null, null, 2021),
                notice("100000002", "Ressource en ligne", null, null, null, 2020));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("ANNÉE", decision.reason());
    }

    private void notices(NoticeXml first, NoticeXml second) throws IOException {
        when(noticeService.getNoticeByPpn("100000001")).thenReturn(first);
        when(noticeService.getNoticeByPpn("100000002")).thenReturn(second);
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

    private static NoticeXml notice(
            String ppn,
            String support,
            String distributor,
            String volume,
            String partTitle,
            Integer year) {
        NoticeXml notice = new NoticeXml();
        notice.setLeader("     nam0 22        450 ");
        notice.setControlfields(List.of(
                controlfield("001", ppn),
                controlfield("008", "Oax3")));

        List<Datafield> fields = new ArrayList<>();
        fields.add(field("530", " ", "b", support));
        if (distributor != null) {
            fields.add(field("214", "2", "c", distributor));
        }
        if (volume != null || partTitle != null) {
            List<String> titleValues = new ArrayList<>();
            if (volume != null) {
                titleValues.add("h");
                titleValues.add(volume);
            }
            if (partTitle != null) {
                titleValues.add("i");
                titleValues.add(partTitle);
            }
            fields.add(field(
                    "200", " ", titleValues.toArray(String[]::new)));
        }
        if (year != null) {
            fields.add(field(
                    "100",
                    " ",
                    "a",
                    "20240723d" + year + "    m  y0frey50      ba"));
            fields.add(field("214", "0", "d", "[" + year + "]"));
        }
        notice.setDatafields(fields);
        return notice;
    }

    private static Controlfield controlfield(String tag, String value) {
        Controlfield field = new Controlfield();
        field.setTag(tag);
        field.setValue(value);
        return field;
    }

    private static Datafield field(
            String tag,
            String ind2,
            String... codeValues) {
        Datafield field = new Datafield();
        field.setTag(tag);
        field.setInd1(" ");
        field.setInd2(ind2);
        List<SubField> subFields = new ArrayList<>();
        for (int index = 0; index < codeValues.length; index += 2) {
            if (codeValues[index + 1] != null) {
                SubField subField = new SubField();
                subField.setCode(codeValues[index]);
                subField.setValue(codeValues[index + 1]);
                subFields.add(subField);
            }
        }
        field.setSubFields(subFields);
        return field;
    }
}
