package fr.abes.bestppn.model.dto.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class LigneKbartDto {
    @CsvBindByName(column = "publication_title")
    @CsvBindByPosition(position = 0)
    @JsonProperty("publication_title")
    private String publicationTitle;
    @CsvBindByName(column = "print_identifier")
    @CsvBindByPosition(position = 1)
    @JsonProperty("print_identifier")
    private String printIdentifier;
    @CsvBindByName(column = "online_identifier")
    @CsvBindByPosition(position = 2)
    @JsonProperty("online_identifier")
    private String onlineIdentifier;
    @CsvBindByName(column = "date_first_issue_online")
    @CsvBindByPosition(position = 3)
    @JsonProperty("date_first_issue_online")
    private String dateFirstIssueOnline;
    @CsvBindByName(column = "num_first_vol_online")
    @CsvBindByPosition(position = 4)
    @JsonProperty("num_first_vol_online")
    private Integer numFirstVolOnline;
    @CsvBindByName(column = "num_first_issue_online")
    @CsvBindByPosition(position = 5)
    @JsonProperty("num_first_issue_online")
    private Integer numFirstIssueOnline;
    @CsvBindByName(column = "date_last_issue_online")
    @CsvBindByPosition(position = 6)
    @JsonProperty("date_last_issue_online")
    private String dateLastIssueOnline;
    @CsvBindByName(column = "num_last_vol_online")
    @CsvBindByPosition(position = 7)
    @JsonProperty("num_last_vol_online")
    private Integer numLastVolOnline;
    @CsvBindByName(column = "num_last_issue_online")
    @CsvBindByPosition(position = 8)
    @JsonProperty("num_last_issue_online")
    private Integer numLastIssueOnline;
    @CsvBindByName(column = "title_url")
    @CsvBindByPosition(position = 9)
    @JsonProperty("title_url")
    private String titleUrl;
    @CsvBindByName(column = "first_author")
    @CsvBindByPosition(position = 10)
    @JsonProperty("first_author")
    private String firstAuthor;
    @CsvBindByName(column = "title_id")
    @CsvBindByPosition(position = 11)
    @JsonProperty("title_id")
    private String titleId;
    @CsvBindByName(column = "embargo_info")
    @CsvBindByPosition(position = 12)
    @JsonProperty("embargo_info")
    private String embargoInfo;
    @CsvBindByName(column = "coverage_depth")
    @CsvBindByPosition(position = 13)
    @JsonProperty("coverage_depth")
    private String coverageDepth;
    @CsvBindByName(column = "notes")
    @CsvBindByPosition(position = 14)
    @JsonProperty("notes")
    private String notes;
    @CsvBindByName(column = "publisher_name")
    @CsvBindByPosition(position = 15)
    @JsonProperty("publisher_name")
    private String publisherName;
    @CsvBindByName(column = "publication_type")
    @CsvBindByPosition(position = 16)
    @JsonProperty("publication_type")
    private String publicationType;
    @CsvBindByName(column = "date_monograph_published_print")
    @CsvBindByPosition(position = 17)
    @JsonProperty("date_monograph_published_print")
    private String dateMonographPublishedPrint;
    @CsvBindByName(column = "date_monograph_published_online")
    @CsvBindByPosition(position = 18)
    @JsonProperty("date_monograph_published_online")
    private String dateMonographPublishedOnline;
    @CsvBindByName(column = "monograph_volume")
    @CsvBindByPosition(position = 19)
    @JsonProperty("monograph_volume")
    private Integer monographVolume;
    @CsvBindByName(column = "monograph_edition")
    @CsvBindByPosition(position = 20)
    @JsonProperty("monograph_edition")
    private String monographEdition;
    @CsvBindByName(column = "first_editor")
    @CsvBindByPosition(position = 21)
    @JsonProperty("first_editor")
    private String firstEditor;
    @CsvBindByName(column = "parent_publication_title_id")
    @CsvBindByPosition(position = 22)
    @JsonProperty("parent_publication_title_id")
    private String parentPublicationTitleId;
    @CsvBindByName(column = "preceding_publication_title_id")
    @CsvBindByPosition(position = 23)
    @JsonProperty("preceding_publication_title_id")
    private String precedingPublicationTitleId;
    @CsvBindByName(column = "access_type")
    @CsvBindByPosition(position = 24)
    @JsonProperty("access_type")
    private String accessType;
    @CsvBindByName(column = "best_ppn")
    @CsvBindByPosition(position = 25)
    @JsonProperty("bestPpn")
    private String bestPpn;

    @JsonProperty("provider_package_package")
    private String providerPackagePackage;
    @JsonProperty("provider_package_date_p")
    private Date providerPackageDateP;
    @JsonProperty("provider_package_idt_provider")
    private Integer providerPackageIdtProvider;
    @JsonProperty("id_provider_package")
    private Integer idProviderPackage;


    @JsonIgnore
    @CsvBindByName(column = "errorType")
    @CsvBindByPosition(position = 26)
    private String errorType;


    @Override
    public int hashCode() {
        return this.publicationTitle.hashCode() * this.onlineIdentifier.hashCode() * this.printIdentifier.hashCode();
    }

    @Override
    public String toString() {
        if (this.publicationTitle != null)
            return "publication title : " + this.publicationTitle + " / publication_type : " + this.publicationType +
                (this.onlineIdentifier.isEmpty() ? "" : " / online_identifier : " + this.onlineIdentifier) +
                (this.printIdentifier.isEmpty() ? "" : " / print_identifier : " + this.printIdentifier);
        return "";
    }

    @JsonIgnore
    public boolean isBestPpnEmpty() {
        return this.bestPpn == null || this.bestPpn.isEmpty();
    }

    @JsonIgnore
    public String getAuthor() {
        return (!this.firstAuthor.isEmpty()) ? this.firstAuthor : this.firstEditor;
    }

    @JsonIgnore
    public String getAnneeFromDate_monograph_published_print() {
        return (this.dateMonographPublishedPrint != null && !this.dateMonographPublishedPrint.isEmpty()) ? this.dateMonographPublishedPrint.substring(0, 4) : this.dateMonographPublishedPrint;
    }

    @JsonIgnore
    public String getAnneeFromDate_monograph_published_online() {
        return (this.dateMonographPublishedOnline != null && !this.dateMonographPublishedOnline.isEmpty()) ? this.dateMonographPublishedOnline.substring(0, 4) : this.dateMonographPublishedOnline;
    }
}
