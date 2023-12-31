package fr.abes.bestppn.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;


public class ExecutionReport {
    @Getter @Setter
    private int nbtotalLines;

    private AtomicInteger nbBestPpnFind;

    private AtomicInteger nbLinesWithInputDataErrors;

    private AtomicInteger nbLinesWithErrorsInBestPPNSearch;

    public ExecutionReport() {
        nbBestPpnFind = new AtomicInteger(0);
        nbLinesWithInputDataErrors = new AtomicInteger(0);
        nbLinesWithErrorsInBestPPNSearch = new AtomicInteger(0);
        nbtotalLines = 0;
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
        return nbtotalLines - nbLinesWithErrorsInBestPPNSearch.get() - nbLinesWithInputDataErrors.get();
    }

    public void incrementNbBestPpnFind() {
        nbBestPpnFind.incrementAndGet();
    }

    public void incrementNbLinesWithInputDataErrors() {
        nbLinesWithInputDataErrors.incrementAndGet();
    }

    public void clear(){
        nbtotalLines = 0;
        nbBestPpnFind.set(0);
        nbLinesWithInputDataErrors.set(0);
        nbLinesWithErrorsInBestPPNSearch.set(0);
    }

    public void incrementNbLinesWithErrorsInBestPPNSearch() {
        nbLinesWithErrorsInBestPPNSearch.incrementAndGet();
    }
}
