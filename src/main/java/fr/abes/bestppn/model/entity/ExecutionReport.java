package fr.abes.bestppn.model.entity;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;


public class ExecutionReport {
    @Getter
    private AtomicInteger nbtotalLines;

    private AtomicInteger nbBestPpnFind;

    private AtomicInteger nbLinesWithInputDataErrors;

    private AtomicInteger nbLinesWithErrorsInBestPPNSearch;

    public ExecutionReport() {
        nbBestPpnFind = new AtomicInteger(0);
        nbLinesWithInputDataErrors = new AtomicInteger(0);
        nbLinesWithErrorsInBestPPNSearch = new AtomicInteger(0);
        nbtotalLines = new AtomicInteger(0);
    }

    public void setNbtotalLines(int nbTotalLignes) {
        this.nbtotalLines.set(nbTotalLignes);
    }

    public int getNbBestPpnFind() {
        return nbBestPpnFind.get();
    }

    public int getNbLinesWithInputDataErrors() {
        return nbLinesWithInputDataErrors.get();
    }

    public int getNbLinesWithErrorsInBestPPNSearch() {
        return nbLinesWithErrorsInBestPPNSearch.get();
    }

    public int getNbLinesOk(){
        return nbtotalLines.get() - nbLinesWithErrorsInBestPPNSearch.get() - nbLinesWithInputDataErrors.get();
    }

    public void incrementNbBestPpnFind() {
        nbBestPpnFind.incrementAndGet();
    }

    public void incrementNbLinesWithInputDataErrors() {
        nbLinesWithInputDataErrors.incrementAndGet();
    }

    public void incrementNbLinesWithErrorsInBestPPNSearch() {
        nbLinesWithErrorsInBestPPNSearch.incrementAndGet();
    }
}
