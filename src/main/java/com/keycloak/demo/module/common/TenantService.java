package com.keycloak.demo.module.common;

import com.keycloak.demo.dto.Messages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;


    public void createTenant(String schemaName){
        try {
            System.out.println("Creating schema: " + schemaName);

            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

            System.out.println("Running Liquibase for: " + schemaName);

            runLiquibase(schemaName);

        } catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }

    private void runLiquibase(String schemaName) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db.changelog/changelog-master.yaml");

        liquibase.setDefaultSchema(schemaName);
        liquibase.setLiquibaseSchema(schemaName);

        liquibase.setShouldRun(true);

        try {
            liquibase.afterPropertiesSet();
        } catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException(Messages.LIQUIBASE_FAILED, e);
        }
    }


}
