package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.ExecutionReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

@Slf4j
@Service
public class LogFileService {

    public enum LoadResult { SUCCESS, REJECTED, FAILED }

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
        createExecutionReport(fileName, executionReport, isForced, LoadResult.SUCCESS);
    }

    /** Publie une synthèse complète de l'étape KBART, sans attester la fin Sudoc. */
    public void createExecutionReport(String fileName, ExecutionReport executionReport, boolean isForced,
                                      LoadResult result) throws IOException {
        Path directory = Path.of(pathToKbart);
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName.replace(".tsv", ".log"));
        String content = "TOTAL LINES : " + executionReport.getNbtotalLines() + System.lineSeparator()
                + "LINES OK : " + executionReport.getNbLinesOk() + System.lineSeparator()
                + "LINES WITH INPUT DATA ERRORS : " + executionReport.getNbLinesWithInputDataErrors() + System.lineSeparator()
                + "LINES WITH ERRORS IN BESTPPN SEARCH : " + executionReport.getNbLinesWithErrorsInBestPPNSearch() + System.lineSeparator()
                + "FORCE_OPTION : " + isForced + System.lineSeparator()
                + "LOAD_RESULT : " + result + System.lineSeparator();
        Path temporary = directory.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        // Mêmes permissions de création que les anciens .log (lecture par les autres services).
        Files.createFile(temporary);
        try {
            Files.writeString(temporary, content);
            // Pas de remplacement non atomique : un échec laisse l'ancienne synthèse intacte.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        log.debug(TECHNICAL, "Synthèse {} publiée avec le résultat {}.", target.getFileName(), result);
    }
}
