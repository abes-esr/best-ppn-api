package fr.abes.bestppn.controller;

import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDoiException;
import fr.abes.bestppn.service.BestPpnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;

@Tag(name = "Calcul du meilleur PPN", description = "API de calcul du meilleur PPN pour une ligne tsv")
@CrossOrigin(origins = "*")
@RestController
@Slf4j
@RequestMapping("/api/v1")
public class BestPpnController {
    private final BestPpnService service;

    public BestPpnController(BestPpnService service) {
        this.service = service;
    }
    @Operation(
            summary = "Permet de calculer le best PPN pour des paramètres donnés",
            description = "Déroule l'algorithme de calcul du best PPN par appel successif à des webservices",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Le bestPpn identifié", content = {@Content(schema = @Schema())}),
                    @ApiResponse(responseCode = "400", description = "Erreur dans les paramètres de la requête", content = {@Content(schema = @Schema())}),
                    @ApiResponse(responseCode = "500", description = "Erreur non prise en charge par le système", content = {@Content(schema = @Schema())}),
            }
    )
    @GetMapping(value = "/bestPpn")
    public String bestPpn(@RequestParam(name = "provider") String provider, @RequestParam(name = "publication_title", required = false) String publicationTitle,
                          @RequestParam(name = "publication_type") String publicationType, @RequestParam(name = "online_identifier", required = false) String onlineIdentifier,
                          @RequestParam(name = "print_identifier", required = false) String printIdentifier, @RequestParam(name = "titleUrl", required = false) String titleUrl,
                          @RequestParam(name = "date_monograph_published_online", required = false) String dateMonographPublishedOnline, @RequestParam(name = "date_monograph_published_print", required = false) String dateMonographPublishedPrint,
                          @RequestParam(name = "first_author", required = false) String firstAuthor, @RequestParam(name = "force", required = false) Boolean force) throws IOException {
        try {
            LigneKbartDto ligneKbartDto = new LigneKbartDto();
            ligneKbartDto.setPublicationType(publicationType);
            ligneKbartDto.setPublicationTitle((publicationTitle != null) ? publicationTitle : "");
            ligneKbartDto.setOnlineIdentifier((onlineIdentifier != null) ? onlineIdentifier : "");
            ligneKbartDto.setPrintIdentifier((printIdentifier != null) ? printIdentifier : "");
            ligneKbartDto.setTitleUrl((titleUrl != null) ? titleUrl : "");
            ligneKbartDto.setDateMonographPublishedPrint((dateMonographPublishedPrint != null) ? dateMonographPublishedPrint : "");
            ligneKbartDto.setDateMonographPublishedOnline((dateMonographPublishedOnline != null) ? dateMonographPublishedOnline : "");
            ligneKbartDto.setFirstAuthor((firstAuthor != null) ? firstAuthor : "");
            boolean injectKafka = (force != null) ? force : false;
            return service.getBestPpn(ligneKbartDto, provider, injectKafka).getPpn();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Une url dans le champ title_url du kbart n'est pas correcte");
        } catch (BestPpnException | RestClientException | IllegalArgumentException | IllegalDoiException e) {
            return e.getMessage();
        }
    }
}
