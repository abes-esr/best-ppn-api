package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.ExecutionReport;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Service
public class ExecutionReportService {
    @Getter
    private ExecutionReport executionReport = new ExecutionReport();

    public void setNbtotalLines(int nbtotalLines) {
        this.executionReport.setNbtotalLines(nbtotalLines);
    }
    public void addNbBestPpnFind(){
        executionReport.incrementNbBestPpnFind();
    }

    public void addNbLinesWithInputDataErrors(){
        executionReport.incrementNbLinesWithInputDataErrors();
    }

    public void addNbLinesWithErrorsInBestPPNSearch(){
        executionReport.incrementNbLinesWithErrorsInBestPPNSearch();
    }

    public void clearExecutionReport() {
        executionReport.clear();
    }


}
