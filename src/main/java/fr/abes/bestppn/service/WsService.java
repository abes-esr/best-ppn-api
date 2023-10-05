package fr.abes.bestppn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.wscall.ResultDat2PpnWebDto;
import fr.abes.bestppn.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.dto.wscall.SearchDatWebDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.utils.ExecutionTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WsService {
    @Value("${url.onlineId2Ppn}")
    private String urlOnlineId2Ppn;

    @Value("${url.printId2Ppn}")
    private String urlPrintId2Ppn;

    @Value("${url.dat2Ppn}")
    private String urlDat2Ppn;

    @Value("${url.doi2Ppn}")
    private String urlDoi2Ppn;

    private final RestTemplate restTemplate;
    private final HttpHeaders headers;

    private final ObjectMapper mapper;

    public WsService(ObjectMapper mapper, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        this.mapper = mapper;
    }


    public String postCall(String url, String requestJson) {
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate.postForObject(url, entity, String.class);
    }

    public String getRestCall(String url, String... params) throws RestClientException {
        StringBuilder formedUrl = new StringBuilder(url);
        for (String param : params) {
            formedUrl.append("/");
            formedUrl.append(param);
        }
        log.info(formedUrl.toString());
        return restTemplate.getForObject(formedUrl.toString(), String.class);
    }

    public String getCall(String url, Map<String, String> params) throws RestClientException {
        StringBuilder formedUrl = new StringBuilder(url);
        if (!params.isEmpty()) {
            formedUrl.append("?");
            for (String key : params.keySet()) {
                formedUrl.append(key);
                formedUrl.append("=");
                formedUrl.append(params.get(key));
                formedUrl.append("&");
            }
            formedUrl.deleteCharAt(formedUrl.length() - 1);
        }
        log.info(formedUrl.toString());
        return restTemplate.getForObject(formedUrl.toString(), String.class);
    }

    @ExecutionTime
    public ResultWsSudocDto callOnlineId2Ppn(String type, String id, @Nullable String provider) throws RestClientException, IllegalArgumentException, BestPpnException {
        return getResultWsSudocDto(type, id, provider, urlOnlineId2Ppn);
    }

    @ExecutionTime
    public ResultWsSudocDto callPrintId2Ppn(String type, String id, @Nullable String provider) throws RestClientException, IllegalArgumentException, BestPpnException {
        return getResultWsSudocDto(type, id, provider, urlPrintId2Ppn);
    }

    private ResultWsSudocDto getResultWsSudocDto(String type, String id, @Nullable String provider, String url) throws RestClientException, IllegalArgumentException, BestPpnException{
        ResultWsSudocDto result = new ResultWsSudocDto();
        try {
            result = mapper.readValue((provider != null && !provider.equals("")) ? getRestCall(url, type, id, provider) : getRestCall(url, type, id), ResultWsSudocDto.class);
        } catch (RestClientException ex) {
            log.info("URL : {} / id : {} / provider : {} : Erreur dans l'acces au webservice.", url, id, provider);
            throw ex;
        } catch (IllegalArgumentException ex) {
            if( ex.getMessage().equals("argument \"content\" is null")) {
                throw new BestPpnException("Aucuns ppn correspondant à l'"+ id);
            } else {
                throw ex;
            }
        } catch (JsonProcessingException ex) {
            throw new RestClientException(ex.getMessage());
        }
        return result;
    }

    @ExecutionTime
    public ResultDat2PpnWebDto callDat2Ppn(String date, String author, String title) throws JsonProcessingException {
        SearchDatWebDto searchDatWebDto = new SearchDatWebDto(title);
        if (author != null && !author.isEmpty()) {
            searchDatWebDto.setAuteur(author);
        }
        if (date != null && !date.isEmpty()) {
            searchDatWebDto.setDate(Integer.valueOf(date));
        }
        return mapper.readValue(postCall(urlDat2Ppn, mapper.writeValueAsString(searchDatWebDto)), ResultDat2PpnWebDto.class);
    }

    @ExecutionTime
    public ResultWsSudocDto callDoi2Ppn(String doi, @Nullable String provider) throws JsonProcessingException {
        Map<String, String> params = new HashMap<>();
        params.put("doi", doi);
        params.put("provider", provider);
        ResultWsSudocDto result = new ResultWsSudocDto();
        try {
            result = mapper.readValue(getCall(urlDoi2Ppn, params), ResultWsSudocDto.class);
        } catch (RestClientException ex) {
            log.info("doi : {} / provider {} : Impossible d'accéder au ws doi2ppn.", doi, provider);
        }
        return result;
    }

}
