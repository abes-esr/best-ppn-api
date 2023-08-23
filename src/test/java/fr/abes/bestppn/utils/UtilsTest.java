package fr.abes.bestppn.utils;

import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.IllegalDateException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

        kbart.setTitle_url("https://doi.org/10.1006/jmbi.1998.2354");

        Assertions.assertEquals("10.1006/jmbi.1998.2354", Utils.extractDOI(kbart));

        kbart.setTitle_url(null);
        Assertions.assertEquals("", Utils.extractDOI(kbart));
    }

    @Test
    void extractDOItestAvecPresenceDOIdanstitleId() {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setTitle_id("https://doi.org/10.1006/jmbi.1998.2354");

        Assertions.assertEquals("10.1006/jmbi.1998.2354", Utils.extractDOI(kbart));

        kbart.setTitle_id(null);
        Assertions.assertEquals("", Utils.extractDOI(kbart));
    }

    @Test
    void extractDOItestAvecPresenceDOIdanstitleUrlMaisSansPrefixeDOI() {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setPublication_title("testtitle");
        kbart.setPublication_type("testtype");
        kbart.setOnline_identifier("10.1006/jmbi.1998.2354");
        kbart.setPrint_identifier("print");

        kbart.setTitle_url("10.1006/jmbi.1998.2354");

        Assertions.assertEquals("10.1006/jmbi.1998.2354", Utils.extractDOI(kbart));
    }

    @Test
    void extractDOItestAvecPresenceDOIdanstitleIdetTitleurl_priorisationTitleUrl() {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setPublication_title("testtitle");
        kbart.setPublication_type("testtype");
        kbart.setOnline_identifier("online");
        kbart.setPrint_identifier("print");

        kbart.setTitle_id("https://doi.org/10.51257/a-v2-r7420");
        kbart.setTitle_url("https://doi.org/10.1038/issn.1476-4687");

        Assertions.assertEquals("10.1038/issn.1476-4687", Utils.extractDOI(kbart));
    }

    @Test
    void extractDate() throws IllegalDateException, ParseException {
        String string = "2023-08-21";
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse(string);

        Assertions.assertEquals(date, Utils.extractDate("SPRINGER_GLOBAL_ALLEBOOKS_2023-08-21.tsv"));
    }
}
