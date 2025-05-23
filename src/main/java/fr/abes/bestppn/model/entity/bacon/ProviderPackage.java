package fr.abes.bestppn.model.entity.bacon;

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
    @Id
    @Column(name = "ID_PROVIDER_PACKAGE")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "providerPackageSeq")
    @SequenceGenerator(name = "providerPackageSeq", sequenceName = "PROVIDER_PACKAGE_SEQ", allocationSize = 1)
    private Integer idProviderPackage;
    @Column(name = "PACKAGE")
    private String packageName;
    @Column(name = "DATE_P")
    private Date dateP;
    @Column(name = "PROVIDER_IDT_PROVIDER")
    private Integer providerIdtProvider;
    @Column(name = "LABEL_ABES")
    private char labelAbes;

    @ManyToOne
    @JoinColumn(referencedColumnName = "IDT_PROVIDER", insertable = false, updatable = false)
    private Provider provider;

    public ProviderPackage(String packageName, Date dateP, Integer providerIdtProvider, char labelAbes) {
        this.packageName = packageName;
        this.dateP = dateP;
        this.providerIdtProvider = providerIdtProvider;
        this.labelAbes = labelAbes;
    }

    @Override
    public String toString() {
        return "{ id:"+idProviderPackage + ", packageName:"+packageName+", providerIdt:"+providerIdtProvider+" dateP:"+dateP+" }";
    }
}
