package fr.abes.bestppn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.wscall.PpnWithTypeDto;
import fr.abes.bestppn.dto.wscall.ResultDat2PpnWebDto;
import fr.abes.bestppn.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SpringBootTest(classes = {BestPpnService.class})
class BestPpnServiceTest {

    @Autowired
    BestPpnService bestPpnService;

    @MockBean
    NoticeService noticeService;

    @MockBean
    TopicProducer topicProducer;

    @MockBean
    CheckUrlService checkUrlService;

    @MockBean
    WsService service;

    @Value("classpath:143519379.xml")
    private Resource xmlFileNoticePrint;

    @Value("classpath:143519380.xml")
    private Resource xmlFileNoticeElec;

    private NoticeXml noticePrint;

    private NoticeXml noticeElec;


    @BeforeEach
    void init() throws IOException {
        String xml = IOUtils.toString(new FileInputStream(xmlFileNoticeElec.getFile()), StandardCharsets.UTF_8);
        JacksonXmlModule module = new JacksonXmlModule();
        module.setDefaultUseWrapper(false);
        XmlMapper mapper = new XmlMapper(module);
        this.noticeElec = mapper.readValue(xml, NoticeXml.class);

        String xml2 = IOUtils.toString(new FileInputStream(xmlFileNoticePrint.getFile()), StandardCharsets.UTF_8);
        JacksonXmlModule module2 = new JacksonXmlModule();
        module2.setDefaultUseWrapper(false);
        XmlMapper mapper2 = new XmlMapper(module2);
        this.noticePrint = mapper2.readValue(xml2, NoticeXml.class);
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 printFromOnline")
    void getBestPpnTest01() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";
        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType1 = new PpnWithTypeDto();
        ppnWithType1.setPpn("100000001");
        ppnWithType1.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType1.setProviderPresent(false);
        PpnWithTypeDto ppnWithType2 = new PpnWithTypeDto();
        ppnWithType2.setPpn("100000002");
        ppnWithType2.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithType2.setProviderPresent(false);
        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        ppnWithTypeDto.add(ppnWithType1);
        ppnWithTypeDto.add(ppnWithType2);
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

       //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");
        kbart.setTitle_url("https://www.test.fr/test");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("100000001", result);
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 printFromOnline with no provider in notice")
    void getBestPpnTest01_WithProviderInNoticeIsPresent() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";
        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType1 = new PpnWithTypeDto();
        ppnWithType1.setPpn("100000001");
        ppnWithType1.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType1.setProviderPresent(true);
        PpnWithTypeDto ppnWithType2 = new PpnWithTypeDto();
        ppnWithType2.setPpn("100000002");
        ppnWithType2.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithType2.setProviderPresent(true);
        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        ppnWithTypeDto.add(ppnWithType1);
        ppnWithTypeDto.add(ppnWithType2);
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");
        kbart.setTitle_url("https://www.test.fr/test");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("100000001", result);
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 elecFromPrint")
    void getBestPpnTest02() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";
        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType1 = new PpnWithTypeDto();
        ppnWithType1.setPpn("100000001");
        ppnWithType1.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType1.setProviderPresent(false);
        PpnWithTypeDto ppnWithType2 = new PpnWithTypeDto();
        ppnWithType2.setPpn("100000002");
        ppnWithType2.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithType2.setProviderPresent(false);
        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        ppnWithTypeDto.add(ppnWithType1);
        ppnWithTypeDto.add(ppnWithType2);
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType3 = new PpnWithTypeDto();
        ppnWithType3.setPpn("200000001");
        ppnWithType3.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType3.setProviderPresent(false);
        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        ppnWithTypePrintDto.add(ppnWithType3);
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");
        kbart.setTitle_url("https://www.test.fr/test");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("100000001", result);
    }

    @Test
    @DisplayName("Test sum of scores")
    void getBestPpnTest03() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";
        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType1 = new PpnWithTypeDto();
        ppnWithType1.setPpn("100000001");
        ppnWithType1.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType1.setProviderPresent(false);
        PpnWithTypeDto ppnWithType2 = new PpnWithTypeDto();
        ppnWithType2.setPpn("100000002");
        ppnWithType2.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType2.setProviderPresent(false);
        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        ppnWithTypeDto.add(ppnWithType1);
        ppnWithTypeDto.add(ppnWithType2);
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType3 = new PpnWithTypeDto();
        ppnWithType3.setPpn("100000001");
        ppnWithType3.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType3.setProviderPresent(false);
        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        ppnWithTypePrintDto.add(ppnWithType3);
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");
        kbart.setTitle_url("https://www.test.fr/test");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("100000001", result);
    }

    @Test
    @DisplayName("Test throw BestPpnException same score")
    void getBestPpnTest04() throws IOException, IllegalPpnException, URISyntaxException {
        String provider = "";
        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType1 = new PpnWithTypeDto();
        ppnWithType1.setPpn("100000001");
        ppnWithType1.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType1.setProviderPresent(false);
        PpnWithTypeDto ppnWithType2 = new PpnWithTypeDto();
        ppnWithType2.setPpn("100000002");
        ppnWithType2.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType2.setProviderPresent(false);
        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        ppnWithTypeDto.add(ppnWithType1);
        ppnWithTypeDto.add(ppnWithType2);
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");
        kbart.setTitle_url("https://www.test.fr/test");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Vérification
        BestPpnException result = Assertions.assertThrows(BestPpnException.class, ()-> bestPpnService.getBestPpn(kbart, provider));
        Assertions.assertEquals("Les ppn électroniques 100000001, 100000002 ont le même score" , result.getLocalizedMessage());
    }

    @Test
    @DisplayName("Test throw BestPpnException with 2 printFromPrint & 2 printFromDat ")
    void getBestPpnTest05() throws IllegalPpnException, IOException {
        String provider = "urlProvider";
        //  Create PpnWithTypeDto for Online
        PpnWithTypeDto ppnWithType1 = new PpnWithTypeDto();
        ppnWithType1.setPpn("100000001");
        ppnWithType1.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithType1.setProviderPresent(false);
        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        ppnWithTypeDto.add(ppnWithType1);
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create a PpnWithTypeDto for print
        PpnWithTypeDto ppnWithTypePrint1 = new PpnWithTypeDto();
        ppnWithTypePrint1.setPpn("200000001");
        ppnWithTypePrint1.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithTypePrint1.setProviderPresent(false);
        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        ppnWithTypePrintDto.add(ppnWithTypePrint1);
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a ResultDat2PpnWebDto
        ResultDat2PpnWebDto resultDat2PpnWeb = new ResultDat2PpnWebDto();
        resultDat2PpnWeb.addPpn("300000001");
        resultDat2PpnWeb.addPpn("300000002");

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("monograph");
        kbart.setDate_monograph_published_print("DateOnline");
        kbart.setDate_monograph_published_online("");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_online(), kbart.getFirst_author(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(noticeService.getNoticeByPpn("300000001")).thenReturn(noticePrint);
        Mockito.when(noticeService.getNoticeByPpn("300000002")).thenReturn(noticePrint);

        //  Vérification
        BestPpnException result = Assertions.assertThrows(BestPpnException.class, ()-> bestPpnService.getBestPpn(kbart, provider));
        Assertions.assertEquals("Plusieurs ppn imprimés (100000001, 200000001, 300000002, 300000001) ont été trouvés." , result.getLocalizedMessage());
    }

    @Test
    @DisplayName("Test printFromPrint & 2 printFromDat ")
    void getBestPpnTest06() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";

        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a ResultDat2PpnWebDto
        ResultDat2PpnWebDto resultDat2PpnWeb = new ResultDat2PpnWebDto();
        resultDat2PpnWeb.addPpn("300000001");
        resultDat2PpnWeb.addPpn("300000002");

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_print("");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_online(), kbart.getFirst_author(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(noticeService.getNoticeByPpn("300000001")).thenReturn(noticeElec);
        Mockito.when(noticeService.getNoticeByPpn("300000002")).thenReturn(noticePrint);

        //  Appel de la méthode
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("300000001", result);
    }

    @Test
    @DisplayName("Test printFromPrint & 0 printFromDat ")
    void getBestPpnTest06_NoBestPpnByDat2Ppn() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";

        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a ResultDat2PpnWebDto
        ResultDat2PpnWebDto resultDat2PpnWeb = new ResultDat2PpnWebDto();

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_print("");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_online(), kbart.getFirst_author(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(noticeService.getNoticeByPpn("300000001")).thenReturn(noticeElec);
        Mockito.when(noticeService.getNoticeByPpn("300000002")).thenReturn(noticePrint);

        //  Appel de la méthode
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("", result);
    }

    @Test
    @DisplayName("Test with 1 elecFromOnline & 1 printFromOnline & titleUrl is null")
    void getBestPpnTest07() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";
        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType1 = new PpnWithTypeDto();
        ppnWithType1.setPpn("100000001");
        ppnWithType1.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType1.setProviderPresent(false);
        PpnWithTypeDto ppnWithType2 = new PpnWithTypeDto();
        ppnWithType2.setPpn("100000002");
        ppnWithType2.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithType2.setProviderPresent(false);
        //  Create a List of PpnWithListDto for elec
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        ppnWithTypeDto.add(ppnWithType1);
        ppnWithTypeDto.add(ppnWithType2);
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");
        kbart.setTitle_url(null);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.any())).thenReturn(true);
        Mockito.when(noticeService.getNoticeByPpn(Mockito.anyString())).thenReturn(this.noticeElec);

        //  Appel du service
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("100000001", result);
    }

    @Test
    @DisplayName("Test with 0 FromOnline & 1 elecFromPrint")
    void getBestPpnTest08() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "";
        //  Create a ResultWsSudocDto for elec
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        resultElec.setPpns(ppnWithTypeDto);

        //  Create PpnWithTypeDto for elec
        PpnWithTypeDto ppnWithType3 = new PpnWithTypeDto();
        ppnWithType3.setPpn("200000001");
        ppnWithType3.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType3.setProviderPresent(false);
        PpnWithTypeDto ppnWithType4 = new PpnWithTypeDto();
        ppnWithType4.setPpn("200000002");
        ppnWithType4.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithType4.setProviderPresent(false);
        //  Create a List of PpnWithListDto for print
        List<PpnWithTypeDto> ppnWithTypePrintDto = new ArrayList<>();
        ppnWithTypePrintDto.add(ppnWithType3);
        ppnWithTypePrintDto.add(ppnWithType4);
        //  Create a ResultWsSudocDto for print
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        resultPrint.setPpns(ppnWithTypePrintDto);

        //  Create a LigneKbartDto
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("1292-8399");
        kbart.setPrint_identifier("2-84358-095-1");
        kbart.setPublication_type("serial");
        kbart.setDate_monograph_published_print("");
        kbart.setDate_monograph_published_online("DateOnline");
        kbart.setPublication_title("Titre");
        kbart.setFirst_author("Auteur");
        kbart.setDate_monograph_published_print("DatePrint");
        kbart.setTitle_url(null);

        //  Mock
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.any())).thenReturn(true);

        //  Appel du service
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("200000001", result);
    }

    @Test
    @DisplayName("Test with 1 elecFromDoi")
    void getBestPpnTest09() throws IllegalPpnException, IOException, BestPpnException, URISyntaxException {
        String provider = "urlProvider";

        //Creation d'une ligne kbart
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setOnline_identifier("9780470059616");
        kbart.setPrint_identifier("9780470032565");
        kbart.setTitle_url("https://onlinelibrary.wiley.com/doi/book/10.1002/9780470059616");
        kbart.setFirst_author("Akyildiz");
        kbart.setTitle_id("10.1002/9780470059616");
        kbart.setCoverage_depth("fulltext");
        kbart.setPublisher_name("John Wiley & Sons, Inc.");
        kbart.setPublication_type("monograph");
        kbart.setDate_monograph_published_print("2009");
        kbart.setDate_monograph_published_online("2009");
        kbart.setMonograph_edition("1");
        kbart.setFirst_editor("Chichester");
        kbart.setParent_publication_title_id("7630");
        kbart.setAccess_type("P");


        //Mock du service Doi -> Les ppn auront un score de 15 (car un seul ppn electro)
        //  Create a ResultDoi2PpnWebDto
        ResultWsSudocDto resultDoi = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnWithTypeDto = new ArrayList<>();
        // Creation ppn electronique pour alimenter ResultDoi2PpnWebDto
        PpnWithTypeDto ppnWithType3 = new PpnWithTypeDto();
        ppnWithType3.setPpn("123456789");
        ppnWithType3.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnWithType3.setProviderPresent(true);
        ppnWithTypeDto.add(ppnWithType3);
        // Creation ppn imprimé pour alimenter ResultDoi2PpnWebDto
        PpnWithTypeDto ppnWithType4 = new PpnWithTypeDto();
        ppnWithType4.setPpn("234567891");
        ppnWithType4.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithTypeDto.add(ppnWithType4);
        //Insertion dans la liste de résultat de ResultDoi2PpnWebDto
        resultDoi.setPpns(ppnWithTypeDto);
        Mockito.when(service.callDoi2Ppn(Utils.extractDOI(kbart), provider)).thenReturn(resultDoi);


        //Mock du service callOnlineId2Ppn -> les ppn auront un score de 10
        ResultWsSudocDto resultElec = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnWithTypeDto2 = new ArrayList<>();
        resultElec.setPpns(ppnWithTypeDto2);
        PpnWithTypeDto ppnElectro = new PpnWithTypeDto();
        ppnElectro.setPpn("200000001");
        ppnElectro.setTypeSupport(TYPE_SUPPORT.ELECTRONIQUE);
        ppnElectro.setProviderPresent(true);
        ppnWithTypeDto2.add(ppnElectro);
        resultElec.setPpns(ppnWithTypeDto2);
        Mockito.when(service.callOnlineId2Ppn(kbart.getPublication_type(), kbart.getOnline_identifier(), provider)).thenReturn(resultElec);


        //Mock du service callPrintId2Ppn -> les ppn auront un score de 8
        ResultWsSudocDto resultPrint = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnWithTypeDto3 = new ArrayList<>();
        resultPrint.setPpns(ppnWithTypeDto3);
        PpnWithTypeDto ppnPrint = new PpnWithTypeDto();
        ppnPrint.setPpn("200000002");
        ppnPrint.setTypeSupport(TYPE_SUPPORT.IMPRIME);
        ppnWithTypeDto3.add(ppnPrint);
        resultPrint.setPpns(ppnWithTypeDto3);
        Mockito.when(service.callPrintId2Ppn(kbart.getPublication_type(), kbart.getPrint_identifier(), provider)).thenReturn(resultPrint);

        //Mock du service callDat2Ppn -> les ppn auront un score de 20
        ResultDat2PpnWebDto resultDat2PpnWeb = new ResultDat2PpnWebDto();
        resultDat2PpnWeb.addPpn("300000001");
        resultDat2PpnWeb.addPpn("300000002");

        ThreadContext.put("package","truc_truc_2000-12-31.tsv");

        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_online(), kbart.getFirst_author(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(service.callDat2Ppn(kbart.getDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublication_title())).thenReturn(resultDat2PpnWeb);
        Mockito.when(checkUrlService.checkUrlInNotice(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        //  Appel du service
        String result = bestPpnService.getBestPpn(kbart, provider);

        //  Vérification
        Assertions.assertEquals("123456789", result);
    }

    @Test
    @DisplayName("test best ppn with score : 1 seule notice électronique")
    void bestPpnWithScoreTest1() throws BestPpnException, JsonProcessingException {
        LigneKbartDto kbart = new LigneKbartDto();
        String provider = "";
        Map<String, Integer> ppnElecResultList = new HashMap<>();
        ppnElecResultList.put("100000001", 10);
        Set<String> ppnPrintResultList = new HashSet<>();

        String result = bestPpnService.getBestPpnByScore(kbart, provider, ppnElecResultList, ppnPrintResultList);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals("100000001", result);
    }

    @Test
    @DisplayName("test best ppn with score : 2 notices électroniques avec score différent")
    void bestPpnWithScoreTest2() throws BestPpnException, JsonProcessingException {
        LigneKbartDto kbart = new LigneKbartDto();
        String provider = "";
        Map<String, Integer> ppnElecResultList = new HashMap<>();
        ppnElecResultList.put("100000001", 5);
        ppnElecResultList.put("100000002", 10);
        Set<String> ppnPrintResultList = new HashSet<>();

        String result = bestPpnService.getBestPpnByScore(kbart, provider, ppnElecResultList, ppnPrintResultList);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals("100000002", result);
    }

    @Test
    void testMax1(){
        Map<String, Integer> map = new HashMap<>();
        map.put("1", 10);
        map.put("2", 20);
        Map<String, Integer> result = Utils.getMaxValuesFromMap(map);
        Assertions.assertEquals(1 ,result.keySet().size());
        Assertions.assertEquals(20 ,result.get("2"));
    }

    @Test
    void testMax2(){
        Map<String, Integer> map = new HashMap<>();
        map.put("1", 10);
        map.put("2", 20);
        map.put("3", 20);
        Map<String, Integer> result = Utils.getMaxValuesFromMap(map);
        Assertions.assertEquals(2 ,result.keySet().size());
        Assertions.assertEquals(20 ,result.get("2"));
        Assertions.assertEquals(20 ,result.get("3"));
        Assertions.assertNull(result.get("1"));
    }
    @Test
    void testMaxVide(){
        Map<String, Integer> map = new HashMap<>();
        Map<String, Integer> result = Utils.getMaxValuesFromMap(map);
        Assertions.assertTrue(result.isEmpty());
    }
}
