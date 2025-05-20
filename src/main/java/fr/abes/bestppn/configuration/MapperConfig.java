package fr.abes.bestppn.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Configuration
public class MapperConfig {
    @Bean
    public XmlMapper xmlMapper() {
        JacksonXmlModule module = new JacksonXmlModule();
        module.setDefaultUseWrapper(false);
        return new XmlMapper(module);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        ObjectMapper objectMapper = builder.build();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    public HttpMessageConverter<String> stringHttpConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50); // Nombre total de connexions
        connectionManager.setDefaultMaxPerRoute(15); // Nombre de connexions par route

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setRetryStrategy(new CustomRetryStrategy())
                .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .build())
                .build();
        RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        restTemplate.setMessageConverters(Collections.synchronizedList(List.of(stringHttpConverter())));
        return restTemplate;
    }

    private static class CustomRetryStrategy implements HttpRequestRetryStrategy {

        @Override
        public boolean retryRequest(HttpRequest httpRequest, IOException e, int execCount, HttpContext httpContext) {
            return execCount <= 3;
        }

        @Override
        public boolean retryRequest(HttpResponse httpResponse, int execCount, HttpContext httpContext) {
            return execCount <= 3;
        }

        @Override
        public TimeValue getRetryInterval(HttpResponse httpResponse, int execCount, HttpContext httpContext) {
            return TimeValue.ofMilliseconds(30L * execCount);
        }
    }
}
