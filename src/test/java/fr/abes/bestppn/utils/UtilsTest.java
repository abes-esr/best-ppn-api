package fr.abes.bestppn.utils;

import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.IllegalDateException;
import jdk.jshell.execution.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;

class UtilsTest {
    @Test
    void extractDomainFromUrlTest1() throws URISyntaxException {
        String url = "https://www.doi.org/test";
        Assertions.assertEquals("www.doi.org", Utils.extractDomainFromUrl(url));
    }

    @Test
    void extractDomainFromUrlTest2() throws URISyntaxException {
        String url = "http://www.doi.org/test";
        Assertions.assertEquals("www.doi.org", Utils.extractDomainFromUrl(url));
    }

    @Test
    void extractDomainFromUrlTest3() {
        String url = "teskljgfklj/test";
        Assertions.assertThrows(URISyntaxException.class, () -> Utils.extractDomainFromUrl(url));
    }

    @Test
    void extractDOItestAvecPresenceDOIdanstitleUrl() {
        LigneKbartDto kbart = new LigneKbartDto();

        kbart.setTitleUrl("https://doi.org/10.1006/jmbi.1998.2354");

        Assertions.assertEquals("10.1006/jmbi.1998.2354", Utils.extractDOI(kbart));

        kbart.setTitleUrl(null);
        Assertions.assertEquals("", Utils.extractDOI(kbart));
    }

    @Test
    void extractDOItestAvecPresenceDOIdanstitleId() {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setTitleId("https://doi.org/10.1006/jmbi.1998.2354");

        Assertions.assertEquals("10.1006/jmbi.1998.2354", Utils.extractDOI(kbart));

        kbart.setTitleId(null);
        Assertions.assertEquals("", Utils.extractDOI(kbart));
    }

    @Test
    void extractDOItestAvecPresenceDOIdanstitleUrlMaisSansPrefixeDOI() {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setPublicationTitle("testtitle");
        kbart.setPublicationType("testtype");
        kbart.setOnlineIdentifier("10.1006/jmbi.1998.2354");
        kbart.setPrintIdentifier("print");

        kbart.setTitleUrl("10.1006/jmbi.1998.2354");

        Assertions.assertEquals("10.1006/jmbi.1998.2354", Utils.extractDOI(kbart));
    }

    @Test
    void extractDOItestAvecPresenceDOIdanstitleIdetTitleurl_priorisationTitleUrl() {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setPublicationTitle("testtitle");
        kbart.setPublicationType("testtype");
        kbart.setOnlineIdentifier("online");
        kbart.setPrintIdentifier("print");

        kbart.setTitleId("https://doi.org/10.51257/a-v2-r7420");
        kbart.setTitleUrl("https://doi.org/10.1038/issn.1476-4687");

        Assertions.assertEquals("10.1038/issn.1476-4687", Utils.extractDOI(kbart));
    }

    @Test
    void extractDate() throws IllegalDateException, ParseException {
        String string = "2023-08-21";
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse(string);

        Assertions.assertEquals(date, Utils.extractDate("SPRINGER_GLOBAL_ALLEBOOKS_2023-08-21.tsv"));
    }

    @Test
    @DisplayName("Test formatDate")
    void testFormatDate() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = "2019";

        Assertions.assertEquals("2019-01-01", Utils.formatDate(date, true).format(format));
        Assertions.assertEquals("2019-12-31", Utils.formatDate(date, false).format(format));

        date = "2019-03-04";
        Assertions.assertEquals("2019-03-04", Utils.formatDate(date, true).format(format));

        date = null;
        Assertions.assertNull(Utils.formatDate(date, true));
    }
}
