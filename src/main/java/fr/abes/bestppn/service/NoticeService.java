package fr.abes.bestppn.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.repository.basexml.NoticesBibioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeService {
    private final NoticesBibioRepository noticesBibioRepository;

    private final XmlMapper xmlMapper;

    /**
     * Récupère une notice bibliographique à partir de son PPN et la désérialise en objet XML.
     * Afin d'éviter la fuite de mémoire liée aux streams LOB d'Oracle et d'éviter de saturer
     * le Persistence Context de la session Hibernate avec des entités gérées au cours du traitement de masse,
     * on extrait directement le XML sous forme de String.
     *
     * @param ppn Identifiant de la notice
     * @return L'objet NoticeXml mappé ou null si la notice n'existe pas ou est vide
     * @throws IOException Si une erreur de lecture/parsing XML survient
     */
    public NoticeXml getNoticeByPpn(String ppn) throws IOException {
        Optional<String> xmlOpt = this.noticesBibioRepository.findDataXmlByPpn(ppn);
        if (xmlOpt.isEmpty() || xmlOpt.get().isEmpty()) {
            return null;
        }
        return xmlMapper.readValue(xmlOpt.get(), NoticeXml.class);
    }
}
