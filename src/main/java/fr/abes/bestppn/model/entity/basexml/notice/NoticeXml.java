package fr.abes.bestppn.model.entity.basexml.notice;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Représente une notice au format d'export UnimarcXML
 */
@NoArgsConstructor
@Getter
@Setter
@JacksonXmlRootElement(localName = "record")
public class NoticeXml {

    @JacksonXmlProperty(localName = "leader")
    private String leader;

    @JacksonXmlProperty(localName = "controlfield")
    private List<Controlfield> controlfields = new ArrayList<>();

    @JacksonXmlProperty(localName = "datafield")
    private List<Datafield> datafields = new ArrayList<>();

    @Override
    public String toString() {
        return "Notice {leader=" + leader + ", ppn=" + getPpn() + "}";
    }


    /**
     * Indique si la notice est en état supprimée
     *
     * @return
     */
    public boolean isDeleted() {
        return leader.charAt(5) == 'd';
    }

    public boolean isNoticeElectronique() {
        return getTypeDocument().startsWith("O");
    }

    public boolean isNoticeImprimee() {
        return getTypeDocument().startsWith("A");
    }

    /**
     * Retourne le type de document de la notice en se basant sur la zone 008
     *
     * @return les x caractères du code correspondant au type de document
     */
    public String getTypeDocument() {
        Optional<Controlfield> typeDocument = controlfields.stream().filter(cf -> cf.getTag().equals("008")).findFirst();
        return typeDocument.map(Controlfield::getValue).orElse(null);
    }

    /**
     * Retourne le type de support de la notice en se basant sur le premier caractère de la 008
     *
     * @return le type de support sous forme d'enum
     */
    public TYPE_SUPPORT getTypeSupport() {
        return switch (getTypeDocument().substring(0, 1)) {
            case "A" -> TYPE_SUPPORT.IMPRIME;
            case "O" -> TYPE_SUPPORT.ELECTRONIQUE;
            default -> TYPE_SUPPORT.AUTRE;
        };
    }

    public String getPpn() {
        Optional<Controlfield> ppn = controlfields.stream().filter(cf -> cf.getTag().equals("001")).findFirst();
        return ppn.map(Controlfield::getValue).orElse(null);
    }

    public List<Datafield> getZoneDollarUWithoutDollar5(String zoneToCheck) {
        List<Datafield> result = new ArrayList<>();
        List<Datafield> zone856 = datafields.stream().filter(datafield -> datafield.getTag().equals(zoneToCheck)).toList();
        zone856.forEach(zone -> {
            if (zone.getSubFields().stream().noneMatch(sousZone -> sousZone.getCode().equals("5"))) {
                result.add(zone);
            }
        });
        return result;
    }
}
