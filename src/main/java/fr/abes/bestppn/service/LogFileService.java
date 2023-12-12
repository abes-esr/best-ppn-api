package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.ExecutionReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@Slf4j
@Service
public class LogFileService {

    /**
     * Méthode qui créé le rapport d'execution dans un fichier log indépendant du reste de l'application
     * @param fileName le nom du fichier
     * @param executionReport le rapport d'exécution qui sert à alimenter le fichier
     * @throws IOException exception levée
     */
    public void createExecutionReport(String fileName, ExecutionReport executionReport, boolean isForced) {
        try {
            // Création du fichier de log
            Logger logger = Logger.getLogger("ExecutionReport");
            FileHandler fh;
            Path source = Path.of(fileName.replace(".tsv", ".log"));
            fh = new FileHandler(String.valueOf(source), 1000, 1);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            logger.setUseParentHandlers(false); // désactive l'affichage du log dans le terminal
            logger.info("TOTAL LINES : " + executionReport.getNbtotalLines() + System.lineSeparator()
                    + "LINES OK : " + executionReport.getNbLinesOk() + System.lineSeparator()
                    + "LINES WITH INPUT DATA ERRORS : " + executionReport.getNbLinesWithInputDataErrors() + System.lineSeparator()
                    + "LINES WITH ERRORS IN BESTPPN SEARCH : " + executionReport.getNbLinesWithErrorsInBestPPNSearch() + System.lineSeparator()
                    + "FORCE_OPTION : " + isForced + System.lineSeparator());

            // Fermeture du fichier de log
            fh.close();

            // Copie le fichier existant vers le répertoire temporaire en ajoutant sa date de création
            if (Files.exists(source)) {

                //  Vérification du chemin et création si inexistant
                String tempLogWithSeparator = "tempLog" + File.separator;
                File chemin = new File(tempLogWithSeparator);
                if (!chemin.isDirectory()) {
                    Files.createDirectory(Paths.get(tempLogWithSeparator));
                }
                Path target = Path.of(tempLogWithSeparator + source);

                //  Déplacement du fichier
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Fichier de log transféré dans le dossier temporaire.");
            }
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
    }
}
