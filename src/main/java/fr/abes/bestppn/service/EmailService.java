package fr.abes.bestppn.service;

import fr.abes.bestppn.dto.mail.PackageKbartDto;
import jakarta.mail.MessagingException;

import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;

public interface EmailService {
    void sendMailWithAttachment(String packageName, PackageKbartDto dataLines) throws MessagingException, NoSuchFileException, DirectoryNotEmptyException;
}
