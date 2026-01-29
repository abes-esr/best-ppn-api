package fr.abes.bestppn.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.abes.bestppn.model.entity.basexml.NoticesBibio;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.repository.basexml.NoticesBibioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeService {
    private final NoticesBibioRepository noticesBibioRepository;

    private final XmlMapper xmlMapper;

    public NoticeXml getNoticeByPpn(String ppn) throws IOException {
        Optional<NoticesBibio> noticeOpt = this.noticesBibioRepository.findByPpn(ppn);
        if (noticeOpt.isEmpty()) {
            return null;
        }
        Clob clob = noticeOpt.get().getDataXml();
        String xmlString = null;
        try (BufferedReader reader = new BufferedReader(clob.getCharacterStream())) {
            xmlString = reader
                    .lines()
                    .collect(Collectors.joining("\n"));
        } catch (SQLException e) {
            log.error(e.getMessage());
        } finally {
            try {
                clob.free();
            } catch (SQLException e) {
                log.error(e.getMessage());
            }
        }
        return(xmlString != null) ? xmlMapper.readValue(xmlString, NoticeXml.class) : null;
    }
}
