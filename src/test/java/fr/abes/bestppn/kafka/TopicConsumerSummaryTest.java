package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.bacon.ProviderPackage;
import fr.abes.bestppn.service.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopicConsumerSummaryTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, KafkaWorkInProgress> pending = new ConcurrentHashMap<>();
    private final BestPpnService bestPpn = mock(BestPpnService.class);
    private final ProviderService providers = mock(ProviderService.class);
    private final TopicProducer producer = mock(TopicProducer.class);
    private final EmailService email = mock(EmailService.class);
    private KbartService kbart;
    private TopicConsumer listener;
    private String base;

    @BeforeEach
    void setUp() throws Throwable {
        base = "TEST_GLOBAL_PACKAGE" + UUID.randomUUID() + "_2026-11-16";
        Provider provider = new Provider("TEST");
        when(providers.findByProvider("TEST")).thenReturn(Optional.of(provider));
        when(providers.handlerProvider(eq(provider), anyString())).thenReturn(new ProviderPackage());
        kbart = spy(new KbartService(bestPpn, producer, providers, pending));
        LogFileService logs = new LogFileService();
        ReflectionTestUtils.setField(logs, "pathToKbart", directory.toString() + "/");
        listener = new TopicConsumer(mapper, kbart, email, logs, pending);
    }

    @AfterEach
    void cleanLegacyTemporaryFiles() throws IOException {
        Thread.interrupted();
        ThreadContext.clearAll();
        // L'ancienne implémentation écrivait d'abord dans le répertoire courant.
        for (String mode : List.of("", "_FORCE", "_BYPASS")) {
            Files.deleteIfExists(Path.of(base + mode + ".log"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "_FORCE", "_BYPASS"})
    void successIsPublishedAfterCommitForEachMode(String mode) throws Throwable {
        String filename = base + mode + ".tsv";
        doAnswer(call -> {
            assertFalse(Files.exists(summary(mode)), "Aucun succès avant le retour du commit");
            return call.callRealMethod();
        }).when(kbart).commitDatas("TEST", filename);

        send(mode, true);

        assertSummary(mode, "SUCCESS", 0);
        verify(kbart).commitDatas("TEST", filename);
        if (mode.equals("_BYPASS")) {
            verify(producer).sendBypassToLoad(anyList(), any(), eq(filename));
            verify(producer, never()).sendKbart(anyList(), any(), anyString());
        } else {
            verify(producer).sendKbart(anyList(), any(), eq(filename));
        }
        assertTrue(pending.isEmpty());
    }

    @Test
    void functionalErrorRejectsTheLoadWithoutCommitting() throws Throwable {
        when(bestPpn.getBestPpn(any(), eq("TEST"), eq(false)))
                .thenThrow(new BestPpnException("Plusieurs PPN de même score"));

        send("", false);

        assertSummary("", "REJECTED", 1);
        verify(kbart, never()).commitDatas(anyString(), anyString());
        verifyNoInteractions(producer);
        verify(email).sendMailWithAttachment(eq(base + ".tsv"), any());
        assertTrue(pending.isEmpty());
    }

    @Test
    void forcedFunctionalErrorStillCommitsAndReportsSuccess() throws Throwable {
        when(bestPpn.getBestPpn(any(), eq("TEST"), eq(true)))
                .thenThrow(new BestPpnException("Plusieurs PPN de même score"));

        send("_FORCE", false);

        assertSummary("_FORCE", "SUCCESS", 1);
        verify(kbart).commitDatas("TEST", base + "_FORCE.tsv");
        assertTrue(pending.isEmpty());
    }

    static Stream<Throwable> commitFailures() {
        return Stream.of(new IOException("Erreur de commit"),
                new ExecutionException(new IOException("Kafka indisponible")),
                new InterruptedException("Commit interrompu"),
                new IllegalDateException(new IllegalArgumentException("Date incorrecte")),
                new IllegalStateException("Erreur technique inattendue"));
    }

    @ParameterizedTest
    @MethodSource("commitFailures")
    void commitFailurePublishesFailedAndReleasesThePackage(Throwable failure) throws Throwable {
        doThrow(failure).when(kbart).commitDatas("TEST", base + ".tsv");

        assertDoesNotThrow(() -> send("", true));

        assertSummary("", "FAILED", 0);
        assertEquals(failure instanceof InterruptedException, Thread.currentThread().isInterrupted());
        verify(email).sendProductionErrorEmail(base + ".tsv", failure.getMessage());
        verify(email, never()).sendMailWithAttachment(anyString(), any());
        assertTrue(pending.isEmpty());
    }

    @Test
    void emailFailureCannotTurnSuccessfulCommitIntoFailed() throws Throwable {
        doThrow(new IllegalStateException("Email indisponible"))
                .when(email).sendMailWithAttachment(anyString(), any());

        assertDoesNotThrow(() -> send("", true));

        assertSummary("", "SUCCESS", 0);
        verify(kbart).commitDatas("TEST", base + ".tsv");
        assertTrue(pending.isEmpty());
    }

    @Test
    void failedErrorNotificationCannotPreventTheFailedSummary() throws Throwable {
        doThrow(new IOException("Commit indisponible")).when(kbart).commitDatas("TEST", base + ".tsv");
        doThrow(new IllegalStateException("Email indisponible"))
                .when(email).sendProductionErrorEmail(anyString(), anyString());

        assertDoesNotThrow(() -> send("", true));

        assertSummary("", "FAILED", 0);
        assertTrue(pending.isEmpty());
    }

    @Test
    void newRejectedAttemptReplacesPreviousSuccessWithoutTouchingAnotherMode() throws Exception {
        send("", true);
        send("_BYPASS", true);
        byte[] bypass = Files.readAllBytes(summary("_BYPASS"));
        when(bestPpn.getBestPpn(any(), eq("TEST"), eq(false)))
                .thenThrow(new BestPpnException("Doublon"));

        send("", false);

        assertSummary("", "REJECTED", 1);
        assertArrayEquals(bypass, Files.readAllBytes(summary("_BYPASS")));
        assertTrue(pending.isEmpty());
    }

    private void send(String mode, boolean knownPpn) throws Exception {
        LigneKbartDto line = new LigneKbartDto();
        line.setNbLinesTotal(1);
        line.setNbCurrentLines(1);
        line.setPublicationTitle("Titre test");
        line.setPublicationType("serial");
        line.setBestPpn(knownPpn ? "123456789" : null);
        listener.kbartFromkafkaListener(new ConsumerRecord<>("kbart", 0, 0,
                base + mode + ".tsv_1", mapper.writeValueAsString(line)));
    }

    private Path summary(String mode) { return directory.resolve(base + mode + ".log"); }

    private void assertSummary(String mode, String result, int errors) throws IOException {
        assertTrue(Files.isRegularFile(summary(mode)), "Une synthèse doit être publiée même en refus/échec");
        assertEquals(List.of("TOTAL LINES : 1", "LINES OK : " + (1 - errors),
                "LINES WITH INPUT DATA ERRORS : 0", "LINES WITH ERRORS IN BESTPPN SEARCH : " + errors,
                "FORCE_OPTION : " + mode.equals("_FORCE"), "LOAD_RESULT : " + result),
                Files.readAllLines(summary(mode)));
    }
}
