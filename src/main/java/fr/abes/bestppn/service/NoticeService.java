package fr.abes.bestppn.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.abes.bestppn.entity.basexml.NoticesBibio;
import fr.abes.bestppn.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.repository.basexml.NoticesBibioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NoticeService {
    private final NoticesBibioRepository noticesBibioRepository;

    private final XmlMapper xmlMapper;

    public NoticeXml getNoticeByPpn(String ppn) throws IllegalPpnException, IOException {
        if (ppn == null)
            throw new IllegalPpnException("Le PPN ne peut pas être null");
        Optional<NoticesBibio> noticeOpt = this.noticesBibioRepository.findByPpn(ppn);
        if (noticeOpt.isPresent()) {
            try {
                return xmlMapper.readValue(noticeOpt.get().getDataXml().getCharacterStream(), NoticeXml.class);
            } catch (SQLException ex) {
                throw new IOException(ex);
            }
        }
        return null;
    }
}
