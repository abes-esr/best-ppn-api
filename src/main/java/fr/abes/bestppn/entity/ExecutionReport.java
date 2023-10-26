package fr.abes.bestppn.entity;

import lombok.Data;

@Data
public class ExecutionReport {
    private int nbtotalLines = 0;

    private int nbBestPpnFind = 0;

    private int nbLinesWithInputDataErrors = 0;

    private int nbLinesWithErrorsInBestPPNSearch = 0;

    public int getNbLinesOk(){
        return nbtotalLines - nbLinesWithErrorsInBestPPNSearch - nbLinesWithInputDataErrors;
    }

    public void addNbBestPpnFind(){
        nbBestPpnFind++;
    }

    public void addNbLinesWithInputDataErrors(){
        nbLinesWithInputDataErrors++;
    }

    public void addNbLinesWithErrorsInBestPPNSearch(){
        nbLinesWithErrorsInBestPPNSearch++;
    }

    public void clear(){
        nbtotalLines = 0;
        nbBestPpnFind = 0;
        nbLinesWithInputDataErrors = 0;
        nbLinesWithErrorsInBestPPNSearch = 0;
    }
}
