package fr.abes.bestppn.utils;

import fr.abes.bestppn.dto.connect.LigneKbartConnect;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class LigneKbartMapper {
    private final UtilsMapper mapper;

    public LigneKbartMapper(UtilsMapper mapper) {
        this.mapper = mapper;
    }

    @Bean
    public void converterLigneKbartDtoLigneKbartConnect() {
        Converter<LigneKbartDto, LigneKbartConnect> myConverter = new Converter<LigneKbartDto, LigneKbartConnect>() {
            @Override
            public LigneKbartConnect convert(MappingContext<LigneKbartDto, LigneKbartConnect> mappingContext) {
                LigneKbartDto ligneKbartDto = mappingContext.getSource();
                LigneKbartConnect ligne = new LigneKbartConnect();
                ligne.setPUBLICATIONTITLE(ligneKbartDto.getPublicationTitle());
                ligne.setPRINTIDENTIFIER(ligneKbartDto.getPrintIdentifier());
                ligne.setONLINEIDENTIFIER(ligneKbartDto.getOnlineIdentifier());
                ligne.setDATEFIRSTISSUEONLINE(Utils.formatDate(ligneKbartDto.getDateFirstIssueOnline(), true));
                ligne.setDATELASTISSUEONLINE(Utils.formatDate(ligneKbartDto.getDateLastIssueOnline(), false));
                ligne.setDATEMONOGRAPHPUBLISHEDPRINT(Utils.formatDate(ligneKbartDto.getDateMonographPublishedPrint(), true));
                ligne.setDATEMONOGRAPHPUBLISHEDONLIN(Utils.formatDate(ligneKbartDto.getDateMonographPublishedOnline(), true));
                ligne.setNUMFIRSTVOLONLINE((ligneKbartDto.getNumFirstVolOnline() != null) ? ligneKbartDto.getNumFirstVolOnline().toString() : "");
                ligne.setNUMFIRSTISSUEONLINE((ligneKbartDto.getNumFirstIssueOnline() != null) ? ligneKbartDto.getNumFirstIssueOnline().toString() : "");
                ligne.setNUMLASTVOLONLINE((ligneKbartDto.getNumLastVolOnline() != null) ? ligneKbartDto.getNumLastVolOnline().toString() : "");
                ligne.setNUMLASTISSUEONLINE((ligneKbartDto.getNumLastIssueOnline() != null) ? ligneKbartDto.getNumLastIssueOnline().toString() : "");
                ligne.setTITLEURL(ligneKbartDto.getTitleUrl());
                ligne.setFIRSTAUTHOR(ligneKbartDto.getFirstAuthor());
                ligne.setTITLEID(ligneKbartDto.getTitleId());
                ligne.setEMBARGOINFO(ligneKbartDto.getEmbargoInfo());
                ligne.setCOVERAGEDEPTH(ligneKbartDto.getCoverageDepth());
                ligne.setNOTES(ligneKbartDto.getNotes());
                ligne.setPUBLISHERNAME(ligneKbartDto.getPublisherName());
                ligne.setPUBLICATIONTYPE(ligneKbartDto.getPublicationType());
                ligne.setMONOGRAPHVOLUME((ligneKbartDto.getMonographVolume() != null) ? ligneKbartDto.getMonographVolume().toString() : "");
                ligne.setMONOGRAPHEDITION(ligneKbartDto.getMonographEdition());
                ligne.setFIRSTEDITOR(ligneKbartDto.getFirstEditor());
                ligne.setPARENTPUBLICATIONTITLEID(ligneKbartDto.getParentPublicationTitleId());
                ligne.setPRECEDINGPUBLICATIONTITLEID(ligneKbartDto.getPrecedingPublicationTitleId());
                ligne.setACCESSTYPE(ligneKbartDto.getAccessType());
                ligne.setPROVIDERPACKAGEPACKAGE(ligneKbartDto.getProviderPackagePackage());
                ligne.setPROVIDERPACKAGEDATEP(Utils.convertDateToLocalDate(ligneKbartDto.getProviderPackageDateP()));
                ligne.setPROVIDERPACKAGEIDTPROVIDER(ligneKbartDto.getProviderPackageIdtProvider());
                ligne.setBESTPPN(ligneKbartDto.getBestPpn());
                return ligne;
            }
        };
        mapper.addConverter(myConverter);
    }
}
