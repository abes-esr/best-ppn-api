package fr.abes.bestppn.entity.bacon;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "PROVIDER_PACKAGE")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProviderPackage implements Serializable {
    @EmbeddedId
    private ProviderPackageId providerPackageId;

    @Column(name = "PACKAGE", insertable=false, updatable=false)
    private String packageName;

    @Column(name = "DATE_P", insertable=false, updatable=false)
    private Date dateP;

    @Column(name = "LABEL_ABES")
    private char labelAbes;

    @Column(name = "PROVIDER_IDT_PROVIDER", insertable=false, updatable=false)
    private Integer providerIdtProvider;

    @ManyToOne
    @JoinColumn(referencedColumnName = "IDT_PROVIDER", insertable = false, updatable = false)
    private Provider provider;

    public ProviderPackage(ProviderPackageId providerPackageId, char labelAbes) {
        this.providerPackageId = providerPackageId;
        this.labelAbes = labelAbes;
    }
}
