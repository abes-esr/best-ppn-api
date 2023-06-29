package fr.abes.bestppn.utils;

import fr.abes.bestppn.exception.IllegalFileFormatException;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class CheckFilesTest {


    @Test
    void isFileWithTSVExtension() throws IllegalFileFormatException {
        MultipartFile file = new MockMultipartFile("test.tsv", "test.tsv", "UTF-8", new byte[]{});
        CheckFiles.isFileWithTSVExtension(file);

        MultipartFile file2 = new MockMultipartFile("test.tsv", "", "UTF-8", new byte[]{});
        IllegalFileFormatException erreur2 = Assertions.assertThrows(IllegalFileFormatException.class, () -> CheckFiles.isFileWithTSVExtension(file2));
        Assertions.assertEquals("Le nom du fichier est vide", erreur2.getMessage());

        MultipartFile file3 = new MockMultipartFile("test.csv", "test.csv", "UTF-8", new byte[]{});
        IllegalFileFormatException erreur3 = Assertions.assertThrows(IllegalFileFormatException.class, () -> CheckFiles.isFileWithTSVExtension(file3));
        Assertions.assertEquals("le fichier n'est pas au format tsv", erreur3.getMessage());
    }

    @Test
    void detectTabulations() throws IOException, IllegalFileFormatException {
        byte[] datas = "test\ttest\ttest".getBytes(StandardCharsets.UTF_8);
        final MultipartFile file = new MockMultipartFile("test.tsv", "test.tsv", "UTF-8", datas);
        CheckFiles.detectTabulations(file);

        datas = "test;test;test".getBytes(StandardCharsets.UTF_8);
        final MultipartFile file2 = new MockMultipartFile("test.tsv", "test.tsv", "UTF-8", datas);
        IllegalFileFormatException erreur = Assertions.assertThrows(IllegalFileFormatException.class, () -> CheckFiles.detectTabulations(file2));
        Assertions.assertEquals("Le fichier ne contient pas de tabulation", erreur.getMessage());
    }

    @Test
    void detectOfHeaderPresence() throws IOException, IllegalFileFormatException {
        byte[] datas = "test\ttest\ttest".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("test.tsv", "test.tsv", "UTF-8", datas);
        CheckFiles.detectHeaderPresence("test", file);

        datas = "toto\ttata\ttiti".getBytes(StandardCharsets.UTF_8);
        MultipartFile file2 = new MockMultipartFile("test.tsv", "test.tsv", "UTF-8", datas);
        IllegalFileFormatException erreur = Assertions.assertThrows(IllegalFileFormatException.class, () -> CheckFiles.detectHeaderPresence("test", file2));
        Assertions.assertEquals("Le champ test est absent de l'en tête du fichier", erreur.getMessage());
    }

    @Test
    void getProviderFromFilename() {
        MultipartFile file = new MockMultipartFile("cairn_Global_Ouvrages-General_2023-02-15.txt", "cairn_Global_Ouvrages-General_2023-02-15.txt", null, (byte[]) null);
        Assertions.assertEquals("cairn", CheckFiles.getProviderFromFilename(file));

        file = new MockMultipartFile("Cairn_Global_Ouvrages-General_2023-02-15.txt", "Cairn_Global_Ouvrages-General_2023-02-15.txt", null, (byte[]) null);
        Assertions.assertEquals("cairn", CheckFiles.getProviderFromFilename(file));
    }

    @Test
    void getDateFromFile() {
        ThreadContext.put("package", "OPENEDITION_GLOBAL_JOURNALS-OPENACCESS-FREEMIUM_2020-04-02.tsv");
        Assertions.assertEquals("02/04/2023", CheckFiles.getDateFromFile("2023"));
    }
}