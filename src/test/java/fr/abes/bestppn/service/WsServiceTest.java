package fr.abes.bestppn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(classes = {WsService.class, ObjectMapper.class, RestTemplate.class})
public class WsServiceTest {
    @Autowired
    WsService wsService;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    RestTemplate restTemplate;

    @Test
    void getCallTest() {
        String url = "http://www.serviceTest.com/service/type/id";
        Mockito.when(restTemplate.getForObject(url, String.class)).thenReturn("test");

        Assertions.assertEquals("test", wsService.getRestCall("http://www.serviceTest.com/service", "type", "id"));
    }

    @Test
    void postCallTest() {
        String url = "http://www.serviceTest.com/service/";
        StringBuilder json = new StringBuilder("{\n");
        json.append("\"test\":\"test\"\n");
        json.append("}");

        Mockito.when(restTemplate.postForObject(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn("test");

        Assertions.assertEquals("test", wsService.postCall(url, json.toString()));
    }
}
