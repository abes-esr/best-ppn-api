package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.basexml.notice.Controlfield;
import fr.abes.bestppn.model.entity.basexml.notice.Datafield;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.entity.basexml.notice.SubField;
import fr.abes.bestppn.model.tiebreak.AccessMode;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CandidateMetadataExtractorTest {
    private final CandidateMetadataExtractor extractor = new CandidateMetadataExtractor();

    @Test
    void extraitToutesLesMetadonneesUtiles() {
        NoticeXml notice = notice(
                field("530", " ", "b", "Ressource en ligne"),
                field("214", "2", "c", "Cairn.info", "d", "[2021]"),
                field("200", " ", "h", "Part II", "i", "Méthodes numériques"),
                field("100", " ", "a", "20240723d2021    m  y0frey50      ba"));

        CandidateMetadata metadata = extractor.extract("123456789", notice);

        assertEquals(AccessMode.ONLINE, metadata.accessMode());
        assertEquals(Set.of("cairn info"), metadata.distributors());
        assertEquals(Set.of("2"), metadata.volumeNumbers());
        assertEquals(Set.of("methodes numeriques"), metadata.partTitles());
        assertEquals(Set.of(2021), metadata.yearsFrom100());
        assertEquals(Set.of(2021), metadata.yearsFrom214());
        assertTrue(metadata.evidence().contains("530$b=Ressource en ligne"));
        assertFalse(metadata.unreadable());
    }

    @Test
    void reconnaitUnSupportPhysiqueDansLesZonesDescriptives() {
        NoticeXml notice = notice(
                field("531", " ", "b", "CD-ROM"),
                field("215", " ", "a", "1 disque optique numérique (DVD-ROM)"));

        assertEquals(AccessMode.PHYSICAL, extractor.extract("123456789", notice).accessMode());
    }

    @Test
    void conserveUnSupportHybrideCommeInconnu() {
        NoticeXml notice = notice(
                field("530", " ", "b", "Ressource en ligne"),
                field("215", " ", "a", "1 CD-ROM"));

        assertEquals(AccessMode.UNKNOWN, extractor.extract("123456789", notice).accessMode());
    }

    @Test
    void neClassePasLaSeuleValeurODe008CommeAccesEnLigne() {
        NoticeXml notice = notice();

        assertEquals(AccessMode.UNKNOWN, extractor.extract("123456789", notice).accessMode());
    }

    @Test
    void neConserveQueLes214QuiPortentLeSecondIndicateur2() {
        NoticeXml notice = notice(
                field("214", "0", "c", "Autre diffuseur"),
                field("214", "2", "c", "Cairn.info"));

        assertEquals(
                Set.of("cairn info"),
                extractor.extract("123456789", notice).distributors());
    }

    @Test
    void extraitPlusieursAnnees214CommeDonneesContradictoires() {
        NoticeXml notice = notice(
                field("214", "0", "d", "DL 2020"),
                field("214", "0", "d", "cop. 2021"));

        CandidateMetadata metadata = extractor.extract("123456789", notice);

        assertEquals(Set.of(2020, 2021), metadata.yearsFrom214());
        assertTrue(metadata.hasContradictoryYears());
    }

    @Test
    void marqueUneNoticeAbsenteOuSupprimeeCommeIllisible() {
        assertTrue(extractor.extract("123456789", null).unreadable());

        NoticeXml deleted = notice();
        deleted.setLeader("     dam0 22        450 ");
        assertTrue(extractor.extract("123456789", deleted).unreadable());
    }

    @Test
    void tolereLesListesEtValeursNulles() {
        NoticeXml notice = new NoticeXml();
        notice.setLeader("     nam0 22        450 ");
        notice.setControlfields(null);
        Datafield malformedField = field("200", " ", "h", null);
        List<SubField> malformedSubfields = new ArrayList<>(malformedField.getSubFields());
        malformedSubfields.add(null);
        malformedField.setSubFields(malformedSubfields);
        List<Datafield> malformedDatafields = new ArrayList<>();
        malformedDatafields.add(null);
        malformedDatafields.add(malformedField);
        notice.setDatafields(malformedDatafields);

        CandidateMetadata metadata = extractor.extract("123456789", notice);

        assertFalse(metadata.unreadable());
        assertTrue(metadata.volumeNumbers().isEmpty());
    }

    private static NoticeXml notice(Datafield... fields) {
        NoticeXml notice = new NoticeXml();
        notice.setLeader("     nam0 22        450 ");
        Controlfield ppn = new Controlfield();
        ppn.setTag("001");
        ppn.setValue("123456789");
        Controlfield type = new Controlfield();
        type.setTag("008");
        type.setValue("Oax3");
        notice.setControlfields(List.of(ppn, type));
        notice.setDatafields(List.of(fields));
        return notice;
    }

    private static Datafield field(String tag, String ind2, String... codeValues) {
        Datafield field = new Datafield();
        field.setTag(tag);
        field.setInd1(" ");
        field.setInd2(ind2);
        List<SubField> subFields = new ArrayList<>();
        for (int index = 0; index < codeValues.length; index += 2) {
            SubField subField = new SubField();
            subField.setCode(codeValues[index]);
            subField.setValue(codeValues[index + 1]);
            subFields.add(subField);
        }
        field.setSubFields(subFields);
        return field;
    }
}
