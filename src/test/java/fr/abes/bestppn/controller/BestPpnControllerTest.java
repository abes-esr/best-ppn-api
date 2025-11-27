package fr.abes.bestppn.controller;

import fr.abes.bestppn.configuration.MapperConfig;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.ExceptionControllerHandler;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.WebApplicationContext;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {BestPpnController.class})
@ContextConfiguration(classes = {MapperConfig.class})
public class BestPpnControllerTest {
    @Autowired
    WebApplicationContext context;

    @MockitoBean
    BestPpnService service;


    MockMvc mockMvc;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(context.getBean(BestPpnController.class))
                .setControllerAdvice(new ExceptionControllerHandler())
                .build();
    }

    @Test
    @DisplayName("test controller with wrong number of parameters")
    void testBestPpnControllerWrongNumberOfParameters() throws Exception {
        Mockito.when(service.getBestPpn(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean())).thenReturn(new BestPpn("1111111111", DESTINATION_TOPIC.BEST_PPN_BACON));
        this.mockMvc.perform(get("/api/v1/bestPpn").characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(result -> Assertions.assertTrue((result.getResolvedException() instanceof MissingServletRequestParameterException)))
                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Required parameter 'provider' is not present.")));
        this.mockMvc.perform(get("/api/v1/bestPpn?provider=cairn").characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(result -> Assertions.assertTrue((result.getResolvedException() instanceof MissingServletRequestParameterException)))
                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Required parameter 'publication_type' is not present.")));

    }

    @Test
    @DisplayName("test controller with wrong URL format")
    void testBestPpnControllerBadUrlFormat() throws Exception {
        Mockito.when(service.getBestPpn(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new URISyntaxException("test", "Format d'URL incorrect"));
        this.mockMvc.perform(get("/api/v1/bestPpn?provider=cairn&publication_type=monograph&print_identifier=9781111111111&title_url=test").characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(result -> Assertions.assertTrue((result.getResolvedException() instanceof IllegalArgumentException)))
                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Une url dans le champ title_url du kbart n'est pas correcte")));
    }

    @Test
    @DisplayName("test controller with BestPpnException")
    void testBestPpnControllerBestPpnException() throws Exception {
        Mockito.when(service.getBestPpn(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new BestPpnException("Plusieurs ppn imprimés"));
        this.mockMvc.perform(get("/api/v1/bestPpn?provider=cairn&publication_type=monograph&print_identifier=9781111111111&title_url=test").characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Plusieurs ppn imprimés")));
    }

    @Test
    @DisplayName("test controller Ok")
    void testBestPpnControllerOk() throws Exception {

        Mockito.when(service.getBestPpn(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean())).thenReturn(new BestPpn("1111111111", DESTINATION_TOPIC.BEST_PPN_BACON));
        this.mockMvc.perform(get("/api/v1/bestPpn?provider=cairn&publication_type=monograph&print_identifier=9781111111111&title_url=test").characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("111111111")));
    }
}
