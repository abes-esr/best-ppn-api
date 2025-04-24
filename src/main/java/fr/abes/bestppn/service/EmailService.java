package fr.abes.bestppn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import fr.abes.bestppn.model.dto.PackageKbartDto;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.dto.mail.MailDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class EmailService {

    @Value("${mail.ws.recipient}")
    private String recipient;

    @Value("${mail.ws.url}")
    protected String url;

    @Value("${spring.profiles.active}")
    private String env;


    @Value("${abes.pathToKbart}")
    private String pathToKbart;

    @Value("${serveur.url}")
    private String serveurUrl;

    public void sendMailWithAttachment(String packageName, PackageKbartDto mailAttachment) {
        try {
            //  Création du chemin d'accès pour le fichier .csv
            Path csvPath = Path.of(pathToKbart+"report/rapport_" + packageName + ".csv");

            //  Création du fichier
            createAttachment(mailAttachment, csvPath);

            //  Création du mail
            String requestJson = mailToJSON(
                    this.recipient,
                    "[KBART2BACON : Rapport BestPPN]" + getTag() + "  " + packageName,
                    "<a href=\"" + serveurUrl + "report/rapport_" + packageName + ".csv" + "\" target=\"_blank\">Cliquez pour télécharger le fichier rapport_" + packageName + ".csv</a>"
            );

            //  Envoi du message par mail
            sendMail(requestJson);

            log.info("L'email a été correctement envoyé à " + recipient);

        } catch (IOException | CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            throw new RuntimeException(e);
        }
    }

    protected void createAttachment(PackageKbartDto dataLines, Path csvPath) throws CsvRequiredFieldEmptyException, CsvDataTypeMismatchException, IOException {
        Writer writer = null;
        try {
            //  Création du fichier
            writer = Files.newBufferedWriter(csvPath);

            //  Création du header
            CSVWriter csvWriter = new CSVWriter(writer,
                    CSVWriter.DEFAULT_SEPARATOR,
                    CSVWriter.NO_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                    CSVWriter.DEFAULT_LINE_END);
            String[] header = { "publication_title", "print_identifier", "online_identifier", "date_first_issue_online", "num_first_vol_online", "num_first_issue_online", "date_last_issue_online", "num_last_vol_online", "num_last_issue_online", "title_url", "first_author", "title_id", "embargo_info", "coverage_depth", "notes", "publisher_name", "publication_type", "date_monograph_published_print", "date_monograph_published_online", "monograph_volume", "monograph_edition", "first_editor", "parent_publication_title_id", "preceding_publication_title_id", "access_type", "bestPpn", "errorType" };
            csvWriter.writeNext(header);


            //  Création du beanToCsvBuilder avec le writer de type LigneKbartDto.class
            StatefulBeanToCsvBuilder<LigneKbartDto> builder = new StatefulBeanToCsvBuilder<>(writer);
            StatefulBeanToCsv<LigneKbartDto> beanWriter = builder.build();

            //  Peuple le fichier csv avec les données
            beanWriter.write(dataLines.getKbartDtos());

            //  Ferme le Writer
            writer.close();
        } catch (IOException | CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            throw new RuntimeException(e);
        }finally {
            if (writer != null) { // Vérification importante pour éviter une NullPointerException
                try {
                    writer.close();
                } catch (IOException closeException) {
                    System.err.println("Erreur lors de la fermeture du writer : " + closeException.getMessage()); // Gestion de l'erreur de fermeture
                    // Dans certains cas, il pourrait être judicieux de relancer une exception ici, selon la criticité de l'erreur.
                }
            }
        }
    }

    protected void sendMail(String requestJson) {
        RestTemplate restTemplate = new RestTemplate(); //appel ws qui envoie le mail
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestJson, headers);

        restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        try {
            restTemplate.postForObject(url + "htmlMail/", entity, String.class); //appel du ws avec
        } catch (Exception e) {
            log.warn("Erreur dans l'envoi du mail d'erreur Sudoc" + e);
        }
        //  Création du l'adresse du ws d'envoi de mails
        HttpPost mail = new HttpPost(this.url + "htmlMail/");

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            httpClient.execute(mail);
        } catch (IOException e) {
            log.warn("Erreur lors de l'envoi du mail. " + e);
        }
    }

    protected String mailToJSON(String to, String subject, String text) {
        String json = "";
        ObjectMapper mapper = new ObjectMapper();
        MailDto mail = new MailDto();
        mail.setApp("convergence");
        mail.setTo(to.split(";"));
        mail.setCc(new String[]{});
        mail.setCci(new String[]{});
        mail.setSubject(subject);
        mail.setText(text);
        try {
            json = mapper.writeValueAsString(mail);
        } catch (JsonProcessingException e) {
            log.warn("Erreur lors de la création du mail. " + e);
        }
        return json;
    }

    public void sendProductionErrorEmail(String packageName, String message) {
        //  Création du mail
        String requestJson = mailToJSON(this.recipient, "[KBART2BACON : erreurs]" + getTag() + " " + packageName, message);

        //  Envoi du message par mail
        sendMail(requestJson);
    }

    private String getTag(){
        if(env.equalsIgnoreCase("PROD")){
            return "";
        } else {
            return "[" + env.toUpperCase() + "]";
        }
    }
}
