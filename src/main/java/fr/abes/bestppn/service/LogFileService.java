package fr.abes.bestppn.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@Slf4j
@Service
public class LogFileService {

    /**
     * Méthode qui créé le rapport d'execution dans un fichier log indépendant du reste de l'application
     * @param fileName le nom du fichier
     * @param totalLines le nombre total de lignes pour le fichier concerné
     * @param linesOk le nombre de lignes OK pour le fichier concerné
     * @param linesWithInputDataErrors le nombre de lignes contenant des erreurs de données
     * @param linesWithErrorsInBestPPNSearch le nombre total de lignes contenant des erreurs lors de la recherche du bestPpn
     * @throws IOException exception levée
     */
    public void createExecutionReport(String fileName, int totalLines, int linesOk, int linesWithInputDataErrors, int linesWithErrorsInBestPPNSearch, boolean injectKafka) throws IOException {
        try {
            // Création du fichier de log
            Logger logger = Logger.getLogger("ExecutionReport");
            FileHandler fh;
            Path source = Path.of(fileName.replaceAll(".tsv", ".log"));
            fh = new FileHandler(String.valueOf(source), 1000, 1);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            logger.setUseParentHandlers(false); // désactive l'affichage du log dans le terminal
            logger.info("TOTAL LINES : " + totalLines + System.lineSeparator()
                    + "LINES OK : " + linesOk + System.lineSeparator()
                    + "LINES WITH INPUT DATA ERRORS : " + linesWithInputDataErrors + System.lineSeparator()
                    + "LINES WITH ERRORS IN BESTPPN SEARCH : " + linesWithErrorsInBestPPNSearch + System.lineSeparator()
                    + "FORCE_OPTION : " + injectKafka + System.lineSeparator());

            // Fermeture du fichier de log
            fh.close();

            // Copie le fichier existant vers le répertoire temporaire en ajoutant sa date de création
            if (source != null && Files.exists(source)) {
                LocalDateTime time = LocalDateTime.now();
                DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss", Locale.FRANCE);
                String date = format.format(time);

                //  Vérification du chemin et création si inexistant
                String tempLog = "tempLog/";
                File chemin = new File("tempLog/");
                if (!chemin.isDirectory()) {
                    Files.createDirectory(Paths.get(tempLog));
                }
                Path target = Path.of("tempLog\\" + date + "_" + source);

                //  Déplacement du fichier
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Fichier de log transféré dans le dossier temporaire.");
            }
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
    }
}