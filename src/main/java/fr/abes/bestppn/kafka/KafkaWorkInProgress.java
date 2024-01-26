package fr.abes.bestppn.kafka;

import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.model.dto.PackageKbartDto;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.ExecutionReport;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    private final AtomicInteger nbLignesTraitees;

    private final AtomicInteger nbActiveThreads;

    private final Semaphore semaphore;

    private final List<LigneKbartDto> kbartToSend;

    private final List<LigneKbartImprime> ppnToCreate;

    private final List<LigneKbartDto> ppnFromKbartToCreate;


    public KafkaWorkInProgress(boolean isForced, boolean isBypassed) {
        this.isForced = isForced;
        this.isBypassed = isBypassed;
        this.mailAttachment = new PackageKbartDto();
        this.isOnError = new AtomicBoolean(false);
        this.nbLignesTraitees = new AtomicInteger(0);
        this.nbActiveThreads = new AtomicInteger(0);
        this.semaphore = new Semaphore(1);
        this.kbartToSend = Collections.synchronizedList(new ArrayList<>());
        this.ppnToCreate = Collections.synchronizedList(new ArrayList<>());
        this.ppnFromKbartToCreate = Collections.synchronizedList(new ArrayList<>());
    }

    public void incrementThreads() {
        this.nbActiveThreads.incrementAndGet();
    }

    public void decrementThreads() {
        this.nbActiveThreads.addAndGet(-1);
    }

    public int getNbActiveThreads() {
        return this.nbActiveThreads.get();
    }

    public void incrementNbLignesTraitees() {
        this.nbLignesTraitees.incrementAndGet();
    }

    public int getNbLignesTraitees() {
        return this.nbLignesTraitees.get();
    }

    public void setIsOnError(boolean error) {
        this.isOnError.set(error);
    }

    public boolean isOnError() {
        return this.isOnError.get();
    }

    public void setNbtotalLinesInExecutionReport(int nbtotalLines) {
        this.executionReport.setNbtotalLines(nbtotalLines);
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

    @PreDestroy
    public void onDestroy() {
        this.semaphore.release();
    }

    public void addPpnToCreate(LigneKbartImprime ligneKbartImprime) {
        this.ppnToCreate.add(ligneKbartImprime);
    }

    public void addPpnFromKbartToCreate(LigneKbartDto ligneFromKafka) {
        this.ppnFromKbartToCreate.add(ligneFromKafka);
    }

    public void addKbartToSend(LigneKbartDto ligneFromKafka) {
        this.kbartToSend.add(ligneFromKafka);
    }
}
