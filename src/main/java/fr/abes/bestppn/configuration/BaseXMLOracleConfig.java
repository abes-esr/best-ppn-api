package fr.abes.bestppn.configuration;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
@EnableJpaRepositories(transactionManagerRef = "baseXmlTransactionManager",
		entityManagerFactoryRef = "baseXmlEntityManager",
		basePackages = "fr.abes.bestppn.repository.basexml")
public class BaseXMLOracleConfig {
	@Value("${spring.jpa.basexml.database-platform}")
	protected String platform;
	@Value("${spring.jpa.basexml.hibernate.ddl-auto}")
	protected String ddlAuto;
	@Value("${spring.jpa.basexml.generate-ddl}")
	protected boolean generateDdl;
	@Value("${spring.jpa.basexml.properties.hibernate.dialect}")
	protected String dialect;
	@Value("${spring.jpa.basexml.show-sql}")
	private boolean showsql;
	@Value("${spring.sql.basexml.init.mode}")
	private String initMode;

	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.basexml")
	public DataSource baseXmlDataSource() {
		return DataSourceBuilder.create().build();
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean baseXmlEntityManager() {
		LocalContainerEntityManagerFactoryBean em
				= new LocalContainerEntityManagerFactoryBean();
		em.setDataSource(baseXmlDataSource());
		em.setPackagesToScan(
				"fr.abes.bestppn.entity.basexml");
		configHibernate(em, platform, showsql, dialect, ddlAuto, generateDdl, initMode);
		return em;
	}

	private void configHibernate(LocalContainerEntityManagerFactoryBean em, String platform, boolean showsql, String dialect, String ddlAuto, boolean generateDdl, String initMode) {
		HibernateJpaVendorAdapter vendorAdapter
				= new HibernateJpaVendorAdapter();
		vendorAdapter.setGenerateDdl(generateDdl);
		vendorAdapter.setShowSql(showsql);
		vendorAdapter.setDatabasePlatform(platform);
		em.setJpaVendorAdapter(vendorAdapter);
		HashMap<String, Object> properties = new HashMap<>();
		properties.put("hibernate.format_sql", true);
		properties.put("hibernate.hbm2ddl.auto", ddlAuto);
		properties.put("hibernate.dialect", dialect);
		properties.put("logging.level.org.hibernate", "DEBUG");
		properties.put("hibernate.type", "trace");
		properties.put("spring.sql.init.mode", initMode);
		em.setJpaPropertyMap(properties);
	}

	@Bean
	public PlatformTransactionManager baseXmlTransactionManager() {
		JpaTransactionManager transactionManager
				= new JpaTransactionManager();
		transactionManager.setEntityManagerFactory(
				baseXmlEntityManager().getObject());
		return transactionManager;
	}

	@Bean
	public JdbcTemplate baseXmlJdbcTemplate() {
		 return new JdbcTemplate(baseXmlDataSource());
	 }

}
