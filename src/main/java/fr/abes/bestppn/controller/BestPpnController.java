package fr.abes.bestppn.controller;

import fr.abes.bestppn.dto.kafka.KbartProviderDto;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.service.BestPpnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;

@Tag(name = "Convergence localhost", description = "Convergence localhost managements APIs")
@CrossOrigin(origins = "*")
@RestController
@Slf4j
@RequestMapping("/v1")
public class BestPpnController {
    private final BestPpnService service;

    public BestPpnController(BestPpnService service) {
        this.service = service;
    }
    @Operation(
            summary = "Permet de calculer le best PPN pour une ligne kbart donnée",
            description = "Déroule l'algorithme de calcul du best PPN par appel successif à des webservices",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Un best PPN a été trouvé", content = {@Content(schema = @Schema())}),
                    @ApiResponse(responseCode = "400", description = "Erreur dans les paramètres de la requête, ligne Kbart incorrecte", content = {@Content(schema = @Schema())}),
                    @ApiResponse(responseCode = "500", description = "Erreur non prise en charge par le système", content = {@Content(schema = @Schema())}),
            }
    )
    @PostMapping(value = "/bestPpn", consumes = MediaType.APPLICATION_JSON_VALUE, name = "bestPpn")
    public String bestPpn(@Parameter(description = "a kbart ligne (separated with tabulations)", required = true) @RequestBody KbartProviderDto kbart) throws IOException, BestPpnException, IllegalPpnException {
        try {
            String[] tsvElementsOnOneLine = kbart.getKbart().split("\t");
            LigneKbartDto ligneKbartDto = constructDto(tsvElementsOnOneLine);
            return service.getBestPpn(ligneKbartDto, kbart.getProvider());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Une url dans un champ title_url du kbart n'est pas correcte");
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("La ligne Kbart ne contient pas le bon nombre de champs");
        }
    }

    /**
     * Construction de la dto
     *
     * @param line ligne en entrée
     * @return Un objet DTO initialisé avec les informations de la ligne
     */
    private LigneKbartDto constructDto(String[] line) throws IndexOutOfBoundsException {
        LigneKbartDto kbartLineInDtoObject = new LigneKbartDto();
        kbartLineInDtoObject.setPublication_title(line[0]);
        kbartLineInDtoObject.setPrint_identifier(line[1]);
        kbartLineInDtoObject.setOnline_identifier(line[2]);
        kbartLineInDtoObject.setDate_first_issue_online(line[3]);
        kbartLineInDtoObject.setNum_first_vol_online(Integer.getInteger(line[4]));
        kbartLineInDtoObject.setNum_first_issue_online(Integer.getInteger(line[5]));
        kbartLineInDtoObject.setDate_last_issue_online(line[6]);
        kbartLineInDtoObject.setNum_last_vol_online(Integer.getInteger(line[7]));
        kbartLineInDtoObject.setNum_last_issue_online(Integer.getInteger(line[8]));
        kbartLineInDtoObject.setTitle_url(line[9]);
        kbartLineInDtoObject.setFirst_author(line[10]);
        kbartLineInDtoObject.setTitle_id(line[11]);
        kbartLineInDtoObject.setEmbargo_info(line[12]);
        kbartLineInDtoObject.setCoverage_depth(line[13]);
        kbartLineInDtoObject.setNotes(line[14]);
        kbartLineInDtoObject.setPublisher_name(line[15]);
        kbartLineInDtoObject.setPublication_type(line[16]);
        kbartLineInDtoObject.setDate_monograph_published_print(line[17]);
        kbartLineInDtoObject.setDate_monograph_published_online(line[18]);
        kbartLineInDtoObject.setMonograph_volume(Integer.getInteger(line[19]));
        kbartLineInDtoObject.setMonograph_edition(line[20]);
        kbartLineInDtoObject.setFirst_editor(line[21]);
        kbartLineInDtoObject.setParent_publication_title_id(line[22]);
        kbartLineInDtoObject.setPreceding_publication_title_id(line[23]);
        kbartLineInDtoObject.setAccess_type(line[24]);
        return kbartLineInDtoObject;
    }
}
