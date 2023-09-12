package fr.abes.bestppn.dto.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LigneKbartDto {
    @CsvBindByName(column = "publication_title")
    @CsvBindByPosition(position = 0)
    private String publication_title;
    @CsvBindByName(column = "print_identifier")
    @CsvBindByPosition(position = 1)
    private String print_identifier;
    @CsvBindByName(column = "online_identifier")
    @CsvBindByPosition(position = 2)
    private String online_identifier;
    @CsvBindByName(column = "date_first_issue_online")
    @CsvBindByPosition(position = 3)
    private String date_first_issue_online;
    @CsvBindByName(column = "num_first_vol_online")
    @CsvBindByPosition(position = 4)
    private Integer num_first_vol_online;
    @CsvBindByName(column = "num_first_issue_online")
    @CsvBindByPosition(position = 5)
    private Integer num_first_issue_online;
    @CsvBindByName(column = "date_last_issue_online")
    @CsvBindByPosition(position = 6)
    private String date_last_issue_online;
    @CsvBindByName(column = "num_last_vol_online")
    @CsvBindByPosition(position = 7)
    private Integer num_last_vol_online;
    @CsvBindByName(column = "num_last_issue_online")
    @CsvBindByPosition(position = 8)
    private Integer num_last_issue_online;
    @CsvBindByName(column = "title_url")
    @CsvBindByPosition(position = 9)
    private String title_url;
    @CsvBindByName(column = "first_author")
    @CsvBindByPosition(position = 10)
    private String first_author;
    @CsvBindByName(column = "title_id")
    @CsvBindByPosition(position = 11)
    private String title_id;
    @CsvBindByName(column = "embargo_info")
    @CsvBindByPosition(position = 12)
    private String embargo_info;
    @CsvBindByName(column = "coverage_depth")
    @CsvBindByPosition(position = 13)
    private String coverage_depth;
    @CsvBindByName(column = "notes")
    @CsvBindByPosition(position = 14)
    private String notes;
    @CsvBindByName(column = "publisher_name")
    @CsvBindByPosition(position = 15)
    private String publisher_name;
    @CsvBindByName(column = "publication_type")
    @CsvBindByPosition(position = 16)
    private String publication_type;
    @CsvBindByName(column = "date_monograph_published_print")
    @CsvBindByPosition(position = 17)
    private String date_monograph_published_print;
    @CsvBindByName(column = "date_monograph_published_online")
    @CsvBindByPosition(position = 18)
    private String date_monograph_published_online;
    @CsvBindByName(column = "monograph_volume")
    @CsvBindByPosition(position = 19)
    private Integer monograph_volume;
    @CsvBindByName(column = "monograph_edition")
    @CsvBindByPosition(position = 20)
    private String monograph_edition;
    @CsvBindByName(column = "first_editor")
    @CsvBindByPosition(position = 21)
    private String first_editor;
    @CsvBindByName(column = "parent_publication_title_id")
    @CsvBindByPosition(position = 22)
    private String parent_publication_title_id;
    @CsvBindByName(column = "preceding_publication_title_id")
    @CsvBindByPosition(position = 23)
    private String preceding_publication_title_id;
    @CsvBindByName(column = "access_type")
    @CsvBindByPosition(position = 24)
    private String access_type;
    @CsvBindByName(column = "access_type")
    @CsvBindByPosition(position = 25)
    private String bestPpn;

    @JsonIgnore
    @CsvBindByName(column = "errorType")
    @CsvBindByPosition(position = 26)
    private String errorType;


    @Override
    public int hashCode() {
        return this.publication_title.hashCode() * this.online_identifier.hashCode() * this.print_identifier.hashCode();
    }

    @Override
    public String toString() {
        if (this.publication_title != null)
            return "publication title : " + this.publication_title + " / publication_type : " + this.publication_type +
                (this.online_identifier.isEmpty() ? "" : " / online_identifier : " + this.online_identifier) +
                (this.print_identifier.isEmpty() ? "" : " / print_identifier : " + this.print_identifier);
        return "";
    }

    @JsonIgnore
    public boolean isBestPpnEmpty() {
        return this.bestPpn == null || this.bestPpn.isEmpty();
    }

    @JsonIgnore
    public String getAuthor() {
        return (!this.first_author.isEmpty()) ? this.first_author : this.first_editor;
    }

    @JsonIgnore
    public String getAnneeFromDate_monograph_published_print() {
        return this.date_monograph_published_print;
    }

    @JsonIgnore
    public String getAnneeFromDate_monograph_published_online() {
        return this.date_monograph_published_online;
    }
}
