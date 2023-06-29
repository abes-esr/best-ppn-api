package fr.abes.bestppn.entity.basexml.notice;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class NoticeXmlTest {

    @Test
    void testgetZoneDollarUWithoutDollar5() {
        NoticeXml notice = new NoticeXml();

        Datafield zone856 = new Datafield();
        zone856.setTag("856");
        List<SubField> subFields = new ArrayList<>();
        SubField ssB = new SubField();
        ssB.setCode("b");
        ssB.setValue("test");
        SubField ssD = new SubField();
        ssD.setCode("d");
        ssD.setValue("toto");
        SubField ssU = new SubField();
        ssU.setCode("u");
        ssU.setValue("https://www.test.abes.fr/truc");
        subFields.add(ssB);
        subFields.add(ssD);
        subFields.add(ssU);
        zone856.setSubFields(subFields);

        Datafield zone856bis = new Datafield();
        zone856bis.setTag("856");
        subFields = new ArrayList<>();
        SubField ssBBis = new SubField();
        ssBBis.setCode("b");
        ssBBis.setValue("tutu");
        SubField ssDBis = new SubField();
        ssDBis.setCode("d");
        ssDBis.setValue("test");
        SubField ssUBis = new SubField();
        ssUBis.setCode("u");
        ssUBis.setValue("https://www.test.fr/tata");
        SubField ss5 = new SubField();
        ss5.setCode("5");
        ss5.setValue("111111111:666666666");
        subFields.add(ssBBis);
        subFields.add(ssDBis);
        subFields.add(ssUBis);
        subFields.add(ss5);
        zone856bis.setSubFields(subFields);

        notice.setDatafields(Lists.newArrayList(zone856, zone856bis));

        List<Datafield> datafields = notice.getZoneDollarUWithoutDollar5("856");
        Assertions.assertEquals(1, datafields.size());
        Assertions.assertEquals(3, datafields.stream().findFirst().get().getSubFields().size());
        Assertions.assertEquals("https://www.test.abes.fr/truc", datafields.stream().findFirst().get().getSubFields().stream().filter(ss -> ss.getCode().equals("u")).findFirst().get().getValue());

    }
}