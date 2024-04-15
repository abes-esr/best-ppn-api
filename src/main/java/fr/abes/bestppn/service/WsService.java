package fr.abes.bestppn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.exception.IllegalDoiException;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.model.dto.wscall.SearchDatWebDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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

    private final HttpHeaders headers;

    private final ObjectMapper mapper;

    public WsService(ObjectMapper mapper) {
        this.headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        this.mapper = mapper;
    }


    public String postCall(String url, String requestJson) {
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
        RestTemplate restTemplate = new RestTemplate();
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
        log.debug(formedUrl.toString());
        RestTemplate restTemplate = new RestTemplate();
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
        log.debug(formedUrl.toString());
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(formedUrl.toString(), String.class);
    }

    public ResultWsSudocDto callOnlineId2Ppn(String type, String id, @Nullable String provider) throws RestClientException, IllegalArgumentException {
        return getResultWsSudocDto(type, id, provider, urlOnlineId2Ppn);
    }

    public ResultWsSudocDto callPrintId2Ppn(String type, String id, @Nullable String provider) throws RestClientException, IllegalArgumentException {
        return getResultWsSudocDto(type, id, provider, urlPrintId2Ppn);
    }

    private ResultWsSudocDto getResultWsSudocDto(String type, String id, @Nullable String provider, String url) throws RestClientException, IllegalArgumentException {
        ResultWsSudocDto result = new ResultWsSudocDto();
        try {
            result = mapper.readValue((provider != null && !provider.isEmpty()) ? getRestCall(url, type, id, provider) : getRestCall(url, type, id), ResultWsSudocDto.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && ex.getMessage().contains("Aucune notice ne correspond à la recherche")) {
                log.info("Aucuns ppn correspondant à l'identifiant " + id);
            } else {
                log.info("URL : {} / id : {} / provider : {} : Erreur dans l'acces au webservice.", url, id, provider);
                throw ex;
            }
        } catch (RestClientException ex) {
            log.info("URL : {} / id : {} / provider : {} : Erreur dans l'acces au webservice.", url, id, provider);
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new RestClientException(ex.getMessage());
        } finally {
            result.setUrl(setUrlWithParams(provider, url, id, type));
        }
        return result;
    }

    public ResultWsSudocDto callDat2Ppn(String date, String author, String title, String providerName) throws JsonProcessingException {
        SearchDatWebDto searchDatWebDto = new SearchDatWebDto(title, providerName);
        if (author != null && !author.isEmpty()) {
            searchDatWebDto.setAuteur(author);
        }
        if (date != null && !date.isEmpty()) {
            searchDatWebDto.setDate(Integer.valueOf(date));
        }
        ResultWsSudocDto result = mapper.readValue(postCall(urlDat2Ppn, mapper.writeValueAsString(searchDatWebDto)), ResultWsSudocDto.class);
        result.setUrl(urlDat2Ppn + "/" + mapper.writeValueAsString(searchDatWebDto));
        return result;
    }

    public ResultWsSudocDto callDoi2Ppn(String doi, @Nullable String provider) throws JsonProcessingException, IllegalDoiException {
        Map<String, String> params = new HashMap<>();
        params.put("doi", doi);
        params.put("provider", provider);
        ResultWsSudocDto result;
        try {
            result = mapper.readValue(getCall(urlDoi2Ppn, params), ResultWsSudocDto.class);
            result.setUrl(urlDoi2Ppn + "?provider=" + provider + "&doi=" + doi);
        } catch (RestClientException ex) {
            if (ex.getMessage().contains("Le DOI n'est pas au bon format")) {
                log.error("doi : {} / provider {} : n'est pas au bon format.", doi, provider);
                throw new IllegalDoiException(ex.getMessage());
            } else {
                log.warn("doi : {} / provider {} : Impossible d'accéder au ws doi2ppn.", doi, provider);
                throw new RestClientException(ex.getMessage());
            }
        }
        return result;
    }

    private String setUrlWithParams(String provider, String url, String id, String type) {
        if ((provider != null && !provider.isEmpty())) {
            return url + "/" + type + "/" + id + "/" + provider;
        } else {
            return url + "/" + type + "/" + id;
        }
    }

}
