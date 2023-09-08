package fr.abes.bestppn.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@Slf4j
@Service
public class LogFileService {

    public void createFileLog(String fileName, int totalLines, int linesOk, int linesWithInputDataErrors, int linesWithErrorsInBestPPNSearch) throws IOException {

        Logger logger = Logger.getLogger("ExecutionReport");
        FileHandler fh;

        try {
            fh = new FileHandler(fileName.replaceAll(".tsv", ".log"), 1000, 1);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            logger.setUseParentHandlers(false); // désactive l'affiche du log dans le terminal
            logger.info("TOTAL LINES : " + totalLines + " / LINES OK : " + linesOk + " / LINES WITH INPUT DATA ERRORS : " + linesWithInputDataErrors + " / LINES WITH ERRORS IN BESTPPN SEARCH : " + linesWithErrorsInBestPPNSearch);
            fh.close();

            // TODO placer le fichier de log au bon emplacement (begonia)
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
    }

}
