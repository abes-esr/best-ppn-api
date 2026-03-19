package fr.abes.bestppn.service;

import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.dto.wscall.NoticeSummaryDto;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import fr.abes.bestppn.utils.Utils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SpringBootTest(classes = {BestPpnService.class})
class BestPpnServiceTest {

    @Autowired
    BestPpnService bestPpnService;

    @MockitoBean
    NoticeService noticeService;

    @MockitoBean
    TopicProducer topicProducer;

    @MockitoBean
    CheckUrlService checkUrlService;

    @MockitoBean
    WsService service;

    @Value("classpath:143519379.xml")
    private Resource xmlFileNoticePrint;

    @Value("classpath:143519380.xml")
    private Resource xmlFileNoticeElec;

    private NoticeXml noticePrint;

    private NoticeXml noticeElec;

    private static final String TITLE_URL = "https://www.test.fr/test";

    @BeforeEach
    void init() throws IOException {
        JacksonXmlModule module = new JacksonXmlModule();
        module.setDefaultUseWrapper(false);
        XmlMapper mapper = new XmlMapper(module);
        this.noticeElec = mapper.readValue(
                IOUtils.toString(new FileInputStream(xmlFileNoticeElec.getFile()), StandardCharsets.UTF_8),
                NoticeXml.class);
        this.noticePrint = mapper.readValue(
                IOUtils.toString(new FileInputStream(xmlFileNoticePrint.getFile()), StandardCharsets.UTF_8),
                NoticeXml.class);
    }

    private LigneKbartDto createSerialKbart(String titleUrl) {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnlineIdentifier("1292-8399");
        kbart.setPrintIdentifier("2-84358-095-1");
        kbart.setPublicationType("serial");
        kbart.setDateMonographPublishedOnline("DateOnline");
        kbart.setPublicationTitle("Titre");
        kbart.setFirstAuthor("Auteur");
        kbart.setDateMonographPublishedPrint("DatePrint");
        kbart.setTitleUrl(titleUrl);
        return kbart;
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 printFromOnline")
    void getBestPpnTest01() throws IOException, BestPpnException, URISyntaxException {
        String provider = "";
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(List.of(
                new NoticeSummaryDto("100000001", TYPE_SUPPORT.ELECTRONIQUE),
                new NoticeSummaryDto("100000002", TYPE_SUPPORT.IMPRIME)
        ));
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();

        LigneKbartDto kbart = createSerialKbart(TITLE_URL);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        //  Vérification
        Assertions.assertEquals("100000001", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 printFromOnline with no provider in notice")
    void getBestPpnTest01_WithProviderInNoticeIsPresent() throws IOException, BestPpnException, URISyntaxException {
        String provider = "";
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(List.of(
                new NoticeSummaryDto("100000001", TYPE_SUPPORT.ELECTRONIQUE, null, true),
                new NoticeSummaryDto("100000002", TYPE_SUPPORT.IMPRIME, null, true)
        ));
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();

        LigneKbartDto kbart = createSerialKbart(TITLE_URL);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        //  Vérification
        Assertions.assertEquals("100000001", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 elecFromPrint")
    void getBestPpnTest02() throws IOException, BestPpnException, URISyntaxException {
        String provider = "";
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(List.of(
                new NoticeSummaryDto("100000001", TYPE_SUPPORT.ELECTRONIQUE),
                new NoticeSummaryDto("100000002", TYPE_SUPPORT.IMPRIME)
        ));

        //  Create NoticeSummaryDto ELEC from Print
        NoticeSummaryDto noticeElecFromPrint = new NoticeSummaryDto("200000001", TYPE_SUPPORT.ELECTRONIQUE);
        noticeElecFromPrint.setFoundByRebound(true);
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(List.of(noticeElecFromPrint));

        LigneKbartDto kbart = createSerialKbart(TITLE_URL);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        //  Vérification
        Assertions.assertEquals("100000001", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("Test sum of scores")
    void getBestPpnTest03() throws IOException, BestPpnException, URISyntaxException {
        String provider = "";
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(List.of(
                new NoticeSummaryDto("100000001", TYPE_SUPPORT.ELECTRONIQUE),
                new NoticeSummaryDto("100000002", TYPE_SUPPORT.ELECTRONIQUE)
        ));

        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(List.of(new NoticeSummaryDto("100000001", TYPE_SUPPORT.ELECTRONIQUE)));

        LigneKbartDto kbart = createSerialKbart(TITLE_URL);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        //  Vérification
        Assertions.assertEquals("100000001", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("Test throw BestPpnException same score")
    void getBestPpnTest04() throws IOException, URISyntaxException {
        String provider = "";
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(List.of(
                new NoticeSummaryDto("100000001", TYPE_SUPPORT.ELECTRONIQUE),
                new NoticeSummaryDto("100000002", TYPE_SUPPORT.ELECTRONIQUE)
        ));
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();

        LigneKbartDto kbart = createSerialKbart(TITLE_URL);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Vérification
        BestPpnException result = Assertions.assertThrows(BestPpnException.class, () -> bestPpnService.getBestPpn(kbart, provider, false));
        Assertions.assertEquals("Plusieurs ppn électroniques (100000001 OU 100000002) ont le même score. [ publication title : Titre / publication_type : serial / online_identifier : 1292-8399 / print_identifier : 2-84358-095-1 / date_monograph_published_online : DateOnline / date_monograph_published_print : DatePrint / title_url : " + TITLE_URL + " ]", result.getLocalizedMessage());
    }

    @Test
    @DisplayName("Test printFromPrint & 0 printFromDat ")
    void getBestPpnTest06_NoBestPpnByDat2Ppn() throws IOException, BestPpnException, URISyntaxException {
        String provider = "";

        LigneKbartDto kbart = createSerialKbart(null);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(new ResultWsSudocDto());
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(new ResultWsSudocDto());
        Mockito.when(noticeService.getNoticeByPpn("300000001")).thenReturn(noticeElec);
        Mockito.when(noticeService.getNoticeByPpn("300000002")).thenReturn(noticePrint);

        //  Appel de la méthode
        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        //  Vérification
        Assertions.assertNull(result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.NO_PPN_FOUND_SUDOC, result.getDestination());
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 printFromOnline & titleUrl is null")
    void getBestPpnTest07() throws IOException, BestPpnException, URISyntaxException {
        String provider = "";
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(List.of(
                new NoticeSummaryDto("100000001", TYPE_SUPPORT.ELECTRONIQUE),
                new NoticeSummaryDto("100000002", TYPE_SUPPORT.IMPRIME)
        ));
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();

        LigneKbartDto kbart = createSerialKbart(null);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.any())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        //  Vérification
        Assertions.assertEquals("100000001", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("Test with 0 FromOnline & 1 elecFromPrint")
    void getBestPpnTest08() throws IOException, BestPpnException, URISyntaxException {
        String provider = "";
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(List.of(
                new NoticeSummaryDto("200000001", TYPE_SUPPORT.ELECTRONIQUE),
                new NoticeSummaryDto("200000002", TYPE_SUPPORT.IMPRIME)
        ));

        LigneKbartDto kbart = createSerialKbart(null);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(new ResultWsSudocDto());
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.any())).thenReturn(true);

        //  Appel du service
        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        //  Vérification
        Assertions.assertEquals("200000001", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("Test with 1 elecFromDoi")
    void getBestPpnTest09() throws IOException, BestPpnException, URISyntaxException {
        String provider = "urlProvider";

        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnlineIdentifier("9780470059616");
        kbart.setPrintIdentifier("9780470032565");
        kbart.setTitleUrl("https://onlinelibrary.wiley.com/doi/book/10.1002/9780470059616");
        kbart.setFirstAuthor("Akyildiz");
        kbart.setTitleId("10.1002/9780470059616");
        kbart.setCoverageDepth("fulltext");
        kbart.setPublisherName("John Wiley & Sons, Inc.");
        kbart.setPublicationType("monograph");
        kbart.setDateMonographPublishedPrint("2009");
        kbart.setDateMonographPublishedOnline("2009");
        kbart.setMonographEdition("1");
        kbart.setFirstEditor("Chichester");
        kbart.setParentPublicationTitleId("7630");
        kbart.setAccessType("P");

        //Mock du service Doi -> Les ppn auront un score de 15 (car un seul ppn electro)
        //  Create a ResultDoi2PpnWebDto
        ResultWsSudocDto resultDoi = new ResultWsSudocDto();
        resultDoi.setPpns(List.of(
                new NoticeSummaryDto("123456789", TYPE_SUPPORT.ELECTRONIQUE, null, true),
                new NoticeSummaryDto("234567891", TYPE_SUPPORT.IMPRIME)
        ));
        Mockito.when(service.callDoi2Ppn(Utils.extractDOI(kbart), provider)).thenReturn(resultDoi);

        //Mock du service callOnlineId2Ppn -> les ppn auront un score de 10
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(List.of(new NoticeSummaryDto("200000001", TYPE_SUPPORT.ELECTRONIQUE, null, true)));
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider)).thenReturn(resultElec);

        //Mock du service callPrintId2Ppn -> les ppn auront un score de 8
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(List.of(new NoticeSummaryDto("200000002", TYPE_SUPPORT.IMPRIME)));
        Mockito.when(service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider)).thenReturn(resultPrint);

        ThreadContext.put("package", "truc_truc_2000-12-31.tsv");
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);

        BestPpn result = bestPpnService.getBestPpn(kbart, provider, false);

        Assertions.assertEquals("123456789", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("test best ppn with score : 1 seule notice électronique")
    void bestPpnWithScoreTest1() throws BestPpnException {
        LigneKbartDto kbart = new LigneKbartDto();
        Map<String, Integer> ppnElecResultList = new HashMap<>();
        ppnElecResultList.put("100000001", 10);
        Set<String> ppnPrintResultList = new HashSet<>();

        BestPpn result = bestPpnService.getBestPpnByScore(kbart, ppnElecResultList, ppnPrintResultList, false);
        Assertions.assertEquals("100000001", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("test best ppn with score : 2 notices électroniques avec score différent")
    void bestPpnWithScoreTest2() throws BestPpnException {
        LigneKbartDto kbart = new LigneKbartDto();
        Map<String, Integer> ppnElecResultList = new HashMap<>();
        ppnElecResultList.put("100000001", 5);
        ppnElecResultList.put("100000002", 10);
        Set<String> ppnPrintResultList = new HashSet<>();

        BestPpn result = bestPpnService.getBestPpnByScore(kbart, ppnElecResultList, ppnPrintResultList, false);
        Assertions.assertEquals("100000002", result.getPpn());
        Assertions.assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    @DisplayName("test best ppn with score : 2 notices électroniques avec score identique et forçage de l'envoie au producer")
    void bestPpnWithScoreTest3() throws BestPpnException {
        LigneKbartDto kbart = new LigneKbartDto();
        Map<String, Integer> ppnElecResultList = new HashMap<>();
        ppnElecResultList.put("100000001", 10);
        ppnElecResultList.put("100000002", 10);
        Set<String> ppnPrintResultList = new HashSet<>();

        BestPpn result = bestPpnService.getBestPpnByScore(kbart, ppnElecResultList, ppnPrintResultList, true);
        Assertions.assertEquals("", result.getPpn());
    }

    @Test
    @DisplayName("test best ppn with score : 2 notices imprimées et forçage de l'envoie au producer")
    void bestPpnWithScoreTest4() throws BestPpnException {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnlineIdentifier("");
        kbart.setPrintIdentifier("");
        Map<String, Integer> ppnElecResultList = new HashMap<>();
        Set<String> ppnPrintResultList = new HashSet<>();
        ppnPrintResultList.add("100000001");
        ppnPrintResultList.add("100000002");

        BestPpn result = bestPpnService.getBestPpnByScore(kbart, ppnElecResultList, ppnPrintResultList, true);
        Assertions.assertEquals("", result.getPpn());
    }

    @Test
    void testMax1() {
        Map<String, Integer> map = new HashMap<>();
        map.put("1", 10);
        map.put("2", 20);
        Map<String, Integer> result = Utils.getMaxValuesFromMap(map);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(20, result.get("2"));
    }

    @Test
    void testMax2() {
        Map<String, Integer> map = new HashMap<>();
        map.put("1", 10);
        map.put("2", 20);
        map.put("3", 20);
        Map<String, Integer> result = Utils.getMaxValuesFromMap(map);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(20, result.get("2"));
        Assertions.assertEquals(20, result.get("3"));
        Assertions.assertNull(result.get("1"));
    }

    @Test
    void testMaxVide() {
        Map<String, Integer> map = new HashMap<>();
        Map<String, Integer> result = Utils.getMaxValuesFromMap(map);
        Assertions.assertTrue(result.isEmpty());
    }
}
