package fr.abes.bestppn.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.abes.bestppn.model.entity.basexml.NoticesBibio;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.repository.basexml.NoticesBibioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeService {
    private final NoticesBibioRepository noticesBibioRepository;

    private final XmlMapper xmlMapper;

    public NoticeXml getNoticeByPpn(String ppn) throws IOException {
        Optional<NoticesBibio> noticeOpt = this.noticesBibioRepository.findByPpn(ppn);
        if (noticeOpt.isPresent()) {
            Clob clob = noticeOpt.get().getDataXml();
            try (Reader reader = clob.getCharacterStream()){
                return xmlMapper.readValue(reader, NoticeXml.class);
            } catch (SQLException e) {
                log.error(e.getMessage());
            } finally {
                try {
                    clob.free();
                } catch (SQLException e) {
                    log.error(e.getMessage());
                }
            }
        }
        return null;
    }
}
