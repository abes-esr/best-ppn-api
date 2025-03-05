package fr.abes.bestppn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpResponse.BodyHandlers.ofString;

@Service
@Slf4j
public class WsService {
    private static final String ERR_ACCES = "URL : {} / id : {} / provider : {} : Erreur dans l'acces au webservice.";
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

    private final RestTemplate restTemplate;

    public WsService(ObjectMapper mapper, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        this.mapper = mapper;
    }


    public String postCall(String url, String requestJson) {
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate.postForObject(url, entity, String.class);

    }

    public String getRestCall(String url, String... params) throws RestClientException {
        StringBuilder formedUrl = new StringBuilder(url);
        for (String param : params) {
            formedUrl.append("/");
            formedUrl.append(param);
        }
        log.debug(formedUrl.toString());
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
        return restTemplate.getForObject(formedUrl.toString(), String.class);
    }

    public ResultWsSudocDto callOnlineId2Ppn(String type, String id, @Nullable String provider) throws RestClientException {
        return getResultWsSudocDto(type, id, provider, urlOnlineId2Ppn);
    }

    public ResultWsSudocDto callPrintId2Ppn(String type, String id, @Nullable String provider) throws RestClientException {
        return getResultWsSudocDto(type, id, provider, urlPrintId2Ppn);
    }

    private ResultWsSudocDto getResultWsSudocDto(String type, String id, @Nullable String provider, String url) throws RestClientException {
        ResultWsSudocDto result = new ResultWsSudocDto();
        try {
            result = mapper.readValue((provider != null && !provider.isEmpty()) ? getRestCall(url, type, id, provider) : getRestCall(url, type, id), ResultWsSudocDto.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && ex.getMessage().contains("Aucune notice ne correspond à la recherche")) {
                log.info("Aucuns ppn correspondant à l'identifiant " + id);
            } else {
                log.info(ERR_ACCES, url, id, provider);
                throw ex;
            }
        } catch (RestClientException ex) {
            log.info(ERR_ACCES, url, id, provider);
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new RestClientException(ex.getMessage());
        } finally {
            result.setUrl(url + "/" + type + "/" + id + "/" + provider);
        }
        return result;
    }

    public ResultWsSudocDto callDoi2Ppn(String doi, @Nullable String provider) throws RestClientException {
        Map<String, String> params = new HashMap<>();
        params.put("doi", doi.toUpperCase());
        params.put("provider", provider);
        ResultWsSudocDto result = new ResultWsSudocDto();
        try {
            String resultCall = getCall(urlDoi2Ppn, params);
            if (!resultCall.isEmpty()) {
                result = mapper.readValue(resultCall, ResultWsSudocDto.class);
            } else {
                log.info("doi : " + doi + " / provider " + provider + " : aucun ppn ne correspond à la recherche");
            }
            result.setUrl(urlDoi2Ppn + "?provider=" + provider + "&doi=" + doi);
        } catch (JsonProcessingException ex) {
            throw new RestClientException(ex.getMessage());
        }
        return result;
    }

    /**
     * Appel ws dat2ppn
     * @param date date au format YYYY à rechercher
     * @param author auteur à rechercher
     * @param title titre à rechercher
     * @param providerName nom du provider
     * @return résultat de l'appel à dat2ppn
     * @throws JsonProcessingException erreur construction objet résultat
     */
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

}
