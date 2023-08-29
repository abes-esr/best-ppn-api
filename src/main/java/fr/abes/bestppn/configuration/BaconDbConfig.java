package fr.abes.bestppn.configuration;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(entityManagerFactoryRef = "baconEntityManager", transactionManagerRef = "baconTransactionManager", basePackages = "fr.abes.bestppn.repository.bacon")
@NoArgsConstructor
@BaconDbConfiguration
public class BaconDbConfig extends AbstractConfig {
    @Value("${spring.jpa.bacon.show-sql}")
    protected boolean showsql;
    @Value("${spring.jpa.bacon.properties.hibernate.dialect}")
    protected String dialect;
    @Value("${spring.jpa.bacon.hibernate.ddl-auto}")
    protected String ddlAuto;
    @Value("${spring.jpa.bacon.database-platform}")
    protected String platform;
    @Value("${spring.jpa.bacon.generate-ddl}")
    protected boolean generateDdl;
    @Value("${spring.sql.bacon.init.mode}")
    protected String initMode;

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.bacon")
    public DataSource baconDataSource() { return DataSourceBuilder.create().build(); }

    @Bean
    public LocalContainerEntityManagerFactoryBean baconEntityManager() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(baconDataSource());
        em.setPackagesToScan(new String[]{"fr.abes.bestppn.entity.bacon"});
        configHibernate(em, platform, showsql, dialect, ddlAuto, generateDdl, initMode);
        return em;
    }

    @Primary
    @Bean
    public PlatformTransactionManager baconTransactionManager(@Qualifier("baconEntityManager") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        final JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        return transactionManager;
    }

}
