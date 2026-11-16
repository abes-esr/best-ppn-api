package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.ExecutionReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LogFileServiceTest {
    @TempDir Path directory;
    private final LogFileService service = new LogFileService();
    private final ExecutionReport report = new ExecutionReport();
    private final String filename = "TEST_GLOBAL_PACKAGE" + UUID.randomUUID() + "_2026-11-16.tsv";

    @BeforeEach
    void setUp() {
        report.setNbtotalLines(3);
        report.incrementNbLinesWithInputDataErrors();
        report.incrementNbLinesWithErrorsInBestPPNSearch();
        ReflectionTestUtils.setField(service, "pathToKbart", directory.toString() + "/");
    }

    @AfterEach
    void cleanLegacyTemporaryFile() throws IOException {
        Files.deleteIfExists(Path.of(filename.replace(".tsv", ".log")));
    }

    @Test
    void publishesCompleteSummaryWithHistoricalHeaderAndExplicitResult() throws IOException {
        service.createExecutionReport(filename, report, true);

        assertEquals(List.of("TOTAL LINES : 3", "LINES OK : 1",
                "LINES WITH INPUT DATA ERRORS : 1", "LINES WITH ERRORS IN BESTPPN SEARCH : 1",
                "FORCE_OPTION : true", "LOAD_RESULT : SUCCESS"), Files.readAllLines(target()));
        assertNoTemporaryFiles();
    }

    @Test
    void replacesPreviousSummaryCompletely() throws IOException {
        Files.writeString(target(), "Ancienne synthèse\nAncienne fin à ne pas conserver\n");

        service.createExecutionReport(filename, report, false);

        assertEquals(6, Files.readAllLines(target()).size());
        assertFalse(Files.readString(target()).contains("Ancienne"));
        assertNoTemporaryFiles();
    }

    @Test
    void createsNestedReportDirectoryWithoutRequiringTrailingSlash() throws IOException {
        Path nested = directory.resolve("nested/reports");
        ReflectionTestUtils.setField(service, "pathToKbart", nested.toString());

        service.createExecutionReport(filename, report, false);

        assertTrue(Files.isRegularFile(nested.resolve(filename.replace(".tsv", ".log"))));
    }

    @Test
    void failedPublicationPreservesExistingTargetAndCleansTemporaryFile() throws IOException {
        // Une cible non remplaçable provoque un véritable échec de publication.
        Files.createDirectory(target());
        Path history = target().resolve("historique.log");
        Files.writeString(history, "Historique intact");

        assertThrows(IOException.class, () -> service.createExecutionReport(filename, report, false));

        assertEquals("Historique intact", Files.readString(history));
        assertNoTemporaryFiles();
        assertFalse(Files.exists(Path.of(filename.replace(".tsv", ".log"))),
                "Aucun fichier intermédiaire ne doit subsister dans le répertoire courant");
    }

    private Path target() { return directory.resolve(filename.replace(".tsv", ".log")); }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            assertEquals(List.of(target()), files.toList());
        }
    }
}
