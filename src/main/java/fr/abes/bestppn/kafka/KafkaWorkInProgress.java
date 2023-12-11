package fr.abes.bestppn.kafka;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class KafkaWorkInProgress {

    private boolean isForced;

    private final AtomicBoolean isOnError;

    private final AtomicInteger nbLignesTraitees;

    private final AtomicInteger nbActiveThreads;

    private final Semaphore semaphore;

    public KafkaWorkInProgress(boolean isForced) {
        this.isForced = isForced;
        this.isOnError = new AtomicBoolean(false);
        this.nbLignesTraitees = new AtomicInteger(0);
        this.nbActiveThreads = new AtomicInteger(0);
        this.semaphore = new Semaphore(1);
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
}
