package fr.abes.bestppn.kafka;

import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.model.dto.PackageKbartDto;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.ExecutionReport;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * classe permettant de suivre le déroulement d'un traitement sur un fichier donné
 */
@Getter
@Setter
@Slf4j
public class KafkaWorkInProgress {

    private boolean isForced;

    private boolean isBypassed;

    private ExecutionReport executionReport = new ExecutionReport();

    private final PackageKbartDto mailAttachment;

    private final AtomicBoolean isOnError;

    private final AtomicInteger currentLine;

    private final AtomicBoolean isCommitting;

    private final AtomicInteger totalLines;

    private final List<LigneKbartDto> kbartToSend;

    private final List<LigneKbartImprime> ppnToCreate;

    private final List<LigneKbartDto> ppnFromKbartToCreate;

    private long timestamp;


    public KafkaWorkInProgress(boolean isForced, boolean isBypassed) {
        this.isForced = isForced;
        this.isBypassed = isBypassed;
        this.mailAttachment = new PackageKbartDto();
        this.isOnError = new AtomicBoolean(false);
        this.currentLine = new AtomicInteger(0);
        this.isCommitting = new AtomicBoolean(false);
        this.totalLines = new AtomicInteger(0);
        this.kbartToSend = Collections.synchronizedList(new ArrayList<>());
        this.ppnToCreate = Collections.synchronizedList(new ArrayList<>());
        this.ppnFromKbartToCreate = Collections.synchronizedList(new ArrayList<>());
        this.timestamp = Calendar.getInstance().getTimeInMillis();
    }

    public void setIsOnError(boolean error) {
        this.isOnError.set(error);
    }

    public boolean isOnError() {
        return this.isOnError.get();
    }

    public void setNbtotalLinesInExecutionReport(int nbtotalLines) {
        this.totalLines.set(nbtotalLines);
        this.executionReport.setNbtotalLines(nbtotalLines);
    }

    /**
     * Définit le nombre total de lignes attendu pour le fichier.
     *
     * @param total nombre total de lignes
     */
    public void setTotalLines(int total) {
        this.totalLines.set(total);
    }

    /**
     * Récupère le nombre total de lignes attendu pour le fichier.
     *
     * @return nombre total de lignes
     */
    public int getTotalLines() {
        return this.totalLines.get();
    }

    public void addNbBestPpnFindedInExecutionReport() {
        executionReport.incrementNbBestPpnFind();
    }

    public void addNbLinesWithInputDataErrorsInExecutionReport() {
        executionReport.incrementNbLinesWithInputDataErrors();
    }

    public void addNbLinesWithErrorsInExecutionReport() {
        executionReport.incrementNbLinesWithErrorsInBestPPNSearch();
    }

    public void addLineKbartToMailAttachementWithErrorMessage(LigneKbartDto kbart, String messageError) {
        kbart.setErrorType(messageError);
        mailAttachment.addKbartDto(kbart);
    }

    public void addLineKbartToMailAttachment(LigneKbartDto dto) {
        mailAttachment.addKbartDto(dto);
    }

    public void addPpnToCreate(LigneKbartImprime ligneKbartImprime) {
        this.ppnToCreate.add(ligneKbartImprime);
    }

    public void addPpnFromKbartToCreate(LigneKbartDto ligneFromKafka) {
        this.ppnFromKbartToCreate.add(ligneFromKafka);
    }

    public synchronized void addKbartToSend(LigneKbartDto ligneFromKafka) {
        this.kbartToSend.add(ligneFromKafka);
        this.currentLine.incrementAndGet();
    }

    public synchronized void incrementCurrentLine() {
        this.currentLine.incrementAndGet();
    }

    /**
     * Nettoie les listes en mémoire pour libérer la heap du Garbage Collector.
     */
    public void clear() {
        this.kbartToSend.clear();
        this.ppnToCreate.clear();
        this.ppnFromKbartToCreate.clear();
        if (this.mailAttachment != null && this.mailAttachment.getKbartDtos() != null) {
            this.mailAttachment.getKbartDtos().clear();
        }
    }
}
