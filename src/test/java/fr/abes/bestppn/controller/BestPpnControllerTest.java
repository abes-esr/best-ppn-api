package fr.abes.bestppn.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.configuration.MapperConfig;
import fr.abes.bestppn.configuration.RestConfiguration;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.ExceptionControllerHandler;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.service.EmailServiceImpl;
import fr.abes.bestppn.kafka.TopicProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {BestPpnController.class})
@ContextConfiguration(classes = {RestConfiguration.class, MapperConfig.class})
public class BestPpnControllerTest {
    @Autowired
    WebApplicationContext context;

    @InjectMocks
    BestPpnController controller;

    @MockBean
    TopicProducer producer;

    @MockBean
    BestPpnService service;

    @MockBean
    EmailServiceImpl emailServiceImpl;

    @Autowired
    ObjectMapper objectMapper;

    MockMvc mockMvc;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(context.getBean(BestPpnController.class))
                .setControllerAdvice(new ExceptionControllerHandler())
                .build();
    }

//    @Test
//    @DisplayName("test controller with wrong file extension")
//    void testKafkaControllerWrongFileExtension() throws Exception {
//        MockMultipartFile fileWithWrongExtension = new MockMultipartFile("file", "FileWithWrongExtension.csv", MediaType.TEXT_PLAIN_VALUE, InputStream.nullInputStream());
//        this.mockMvc.perform(multipart("/v1/kbart2Kafka").file(fileWithWrongExtension).characterEncoding(StandardCharsets.UTF_8))
//                .andExpect(status().isBadRequest())
//                .andExpect(result -> Assertions.assertTrue((result.getResolvedException() instanceof IllegalArgumentException)))
//                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("le fichier n'est pas au format tsv")));
//    }
//
//    @Test
//    @DisplayName("test controller with wrong file format")
//    void testKafkaControllerWrongFileFormat() throws Exception {
//        MockMultipartFile fileWithWrongFormat = new MockMultipartFile("file", "FileWithWrongFormat.tsv", MediaType.TEXT_PLAIN_VALUE, "test;test;test".getBytes(StandardCharsets.UTF_8));
//        this.mockMvc.perform(multipart("/v1/kbart2Kafka").file(fileWithWrongFormat).characterEncoding(StandardCharsets.UTF_8))
//                .andExpect(status().isBadRequest())
//                .andExpect(result -> Assertions.assertTrue((result.getResolvedException() instanceof IllegalArgumentException)))
//                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Le fichier ne contient pas de tabulation")));
//    }
//
//    @Test
//    @DisplayName("test controller with no header")
//    void testKafkaControllerWrongFileNoHeader() throws Exception {
//        MockMultipartFile fileWithNoHeader = new MockMultipartFile("file", "FileWithWrongFormat.tsv", MediaType.TEXT_PLAIN_VALUE, "test\ttest\ttest".getBytes(StandardCharsets.UTF_8));
//        this.mockMvc.perform(multipart("/v1/kbart2Kafka").file(fileWithNoHeader).characterEncoding(StandardCharsets.UTF_8))
//                .andExpect(status().isBadRequest())
//                .andExpect(result -> Assertions.assertTrue((result.getResolvedException() instanceof IllegalArgumentException)))
//                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Le champ publication_title est absent de l'en tête du fichier")));
//    }
//
//    @Test
//    @DisplayName("test controller all ok without bestPpn")
//    void testKafkaControllerAllOkWithoutBestPpn() throws Exception, IllegalPpnException, BestPpnException {
//        MockMultipartFile file = new MockMultipartFile("file", "cairn_global.tsv", MediaType.TEXT_PLAIN_VALUE, ("publication_title\tprint_identifier\tonline_identifier\tdate_first_issue_online\tnum_first_vol_online\tnum_first_issue_online\tdate_last_issue_online\tnum_last_vol_online\tnum_last_issue_online\ttitle_url\tfirst_author\ttitle_id\tembargo_info\tcoverage_depth\tnotes\tpublisher_name\tpublication_type\tdate_monograph_published_print\tdate_monograph_published_online\tmonograph_volume\tmonograph_edition\tfirst_editor\tparent_publication_title_id\tpreceding_publication_title_id\taccess_type\n" + "Villes et politiques urbaines au Canada et aux États-Unis\t9782878541496\t9782878548808\t\t\t\t\t\t\thttp://books.openedition.org/psn/4795\tLacroix\tpsn/4795\t\tfulltext\t\tPresses Sorbonne Nouvelle\tmonograph\t1997\t2018\t\t\tLacroix\t\t\tF\t225228076\tMonographie.").getBytes(StandardCharsets.UTF_8));
//        Mockito.when(service.getBestPpn(Mockito.any(), eq("cairn"))).thenReturn("123456789");
//        Mockito.doNothing().when(producer).sendKbart(Mockito.any(), Mockito.anyString());
//        this.mockMvc.perform(multipart("/v1/kbart2Kafka").file(file).characterEncoding(StandardCharsets.UTF_8))
//            .andExpect(status().isOk());
//    }
//
//    @Test
//    @DisplayName("test controller all ok with bestPpn")
//    void testKafkaControllerAllOkWithBestPpn() throws Exception, IllegalPpnException, BestPpnException {
//        MockMultipartFile file = new MockMultipartFile("file", "cairn_global.tsv", MediaType.TEXT_PLAIN_VALUE, ("publication_title\tprint_identifier\tonline_identifier\tdate_first_issue_online\tnum_first_vol_online\tnum_first_issue_online\tdate_last_issue_online\tnum_last_vol_online\tnum_last_issue_online\ttitle_url\tfirst_author\ttitle_id\tembargo_info\tcoverage_depth\tnotes\tpublisher_name\tpublication_type\tdate_monograph_published_print\tdate_monograph_published_online\tmonograph_volume\tmonograph_edition\tfirst_editor\tparent_publication_title_id\tpreceding_publication_title_id\taccess_type\n" + "Villes et politiques urbaines au Canada et aux États-Unis\t9782878541496\t9782878548808\t\t\t\t\t\t\thttp://books.openedition.org/psn/4795\tLacroix\tpsn/4795\t\tfulltext\t\tPresses Sorbonne Nouvelle\tmonograph\t1997\t2018\t\t\tLacroix\t\t\tF\t225228076\tMonographie.\t123456789").getBytes(StandardCharsets.UTF_8));
//        Mockito.when(service.getBestPpn(Mockito.any(), eq("cairn"))).thenReturn("123456789");
//        Mockito.doNothing().when(producer).sendKbart(Mockito.any(), Mockito.anyString());
//        this.mockMvc.perform(multipart("/v1/kbart2Kafka").file(file).characterEncoding(StandardCharsets.UTF_8))
//                .andExpect(status().isOk());
//    }
}
