package fr.abes.bestppn.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.abes.bestppn.model.entity.basexml.NoticesBibio;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.repository.basexml.NoticesBibioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.Optional;

import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeService {
    private final NoticesBibioRepository noticesBibioRepository;

    private final XmlMapper xmlMapper;

    @PersistenceContext(unitName = "baseXmlEntityManager")
    private EntityManager entityManager;

    public NoticeXml getNoticeByPpn(String ppn) throws IOException {
        Optional<NoticesBibio> noticeOpt = this.noticesBibioRepository.findByPpn(ppn);
        if (noticeOpt.isEmpty()) {
            return null;
        }
        NoticesBibio noticesBibio = noticeOpt.get();
        Clob clob = noticesBibio.getDataXml();

        try (Reader reader = clob.getCharacterStream()) {
            NoticeXml noticeXml = xmlMapper.readValue(reader, NoticeXml.class);
            entityManager.detach(noticesBibio); // Détachement immédiat pour libérer la mémoire Heap
            return noticeXml;
        } catch (SQLException e) {
            log.error(TECHNICAL, e.getMessage(), e);
            return null;
        } finally {
            try {
                clob.free();
            } catch (SQLException e) {
                log.error(TECHNICAL, e.getMessage(), e);
            }
        }
    }
}
