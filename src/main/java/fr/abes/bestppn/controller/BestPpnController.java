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
                          @RequestParam(name = "print_identifier") String printIdentifier, @RequestParam(name = "doi", required = false) String doi,
                          @RequestParam(name = "date_monograph_published_online", required = false) String dateMonographPublishedOnline, @RequestParam(name = "date_monograph_published_print", required = false) String dateMonographPublishedPrint,
                          @RequestParam(name = "first_author", required = false) String firstAuthor) throws IOException, BestPpnException, IllegalPpnException {
        try {
            LigneKbartDto ligneKbartDto = new LigneKbartDto();
            ligneKbartDto.setPublication_type(publicationType);
            ligneKbartDto.setPublication_title((publicationTitle != null) ? publicationTitle : "");
            ligneKbartDto.setOnline_identifier((onlineIdentifier != null) ? onlineIdentifier : "");
            ligneKbartDto.setPrint_identifier((printIdentifier != null) ? printIdentifier : "");
            ligneKbartDto.setTitle_url((doi != null) ? doi : "");
            ligneKbartDto.setDate_monograph_published_print((dateMonographPublishedPrint != null) ? dateMonographPublishedPrint : "");
            ligneKbartDto.setDate_monograph_published_online((dateMonographPublishedOnline != null) ? dateMonographPublishedOnline : "");
            ligneKbartDto.setFirst_author((firstAuthor != null) ? firstAuthor : "");
            return service.getBestPpn(ligneKbartDto, provider, false);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Une url dans le champ doi du kbart n'est pas correcte");
        } catch (BestPpnException e) {
            return e.getMessage();
        }
    }
}
