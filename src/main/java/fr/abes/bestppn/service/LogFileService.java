package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.ExecutionReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

@Slf4j
@Service
public class LogFileService {

    @Value("${abes.pathToKbart}")
    private String pathToKbart;

    /**
     * Méthode qui créé le rapport d'execution dans un fichier log indépendant du reste de l'application
     *
     * @param fileName        le nom du fichier
     * @param executionReport le rapport d'exécution qui sert à alimenter le fichier
     * @param isForced        indicateur si le traitement est forcé
     */
    public void createExecutionReport(String fileName, ExecutionReport executionReport, boolean isForced) throws IOException {
        Path source = Path.of(fileName.replace(".tsv", ".log"));
        Files.createFile(source);
        Files.writeString(source, "TOTAL LINES : " + executionReport.getNbtotalLines() + System.lineSeparator()
                + "LINES OK : " + executionReport.getNbLinesOk() + System.lineSeparator()
                + "LINES WITH INPUT DATA ERRORS : " + executionReport.getNbLinesWithInputDataErrors() + System.lineSeparator()
                + "LINES WITH ERRORS IN BESTPPN SEARCH : " + executionReport.getNbLinesWithErrorsInBestPPNSearch() + System.lineSeparator()
                + "FORCE_OPTION : " + isForced + System.lineSeparator());
        //  Vérification du chemin et création si inexistant
        File chemin = new File(pathToKbart);
        if (!chemin.isDirectory()) {
            Files.createDirectory(Paths.get(pathToKbart));
        }
        Path target = Path.of(pathToKbart + source);

        //  Déplacement du fichier
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug(TECHNICAL, "Fichier de log {} transféré dans le dossier temporaire.", source.getFileName());
    }
}
