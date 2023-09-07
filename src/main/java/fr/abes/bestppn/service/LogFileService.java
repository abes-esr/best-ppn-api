package fr.abes.bestppn.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;

@Slf4j
@Service
public class LogFileService {

    public void createFileLog(String timestamp, String fileName, int totalLines, int linesOk, int linesWithInputDataErrors, int linesWithErrorsInBestPPNSearch) throws IOException {

        File fichier = new File(fileName.replaceAll(".tsv", ".log"));
        Writer writer = null;

        try {
            writer = new FileWriter(fichier);
            writer.write(timestamp + " INFO - Total lines : " + totalLines);
            writer.write("\n" + timestamp + " INFO - Lines OK : " + linesOk);
            writer.write("\n" + timestamp + " INFO - Lines with input data errors : " + linesWithInputDataErrors);
            writer.write("\n" + timestamp + " INFO - Lines with errors in bestPpn search : " + linesWithErrorsInBestPPNSearch);
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new IOException(e);
                }
            }
        }

        // TODO placer le fichier de log au bon emplacement

        //  Suppression du log temporaire
//        Files.deleteIfExists(logPath);
    }

}
