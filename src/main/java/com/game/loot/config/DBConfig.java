package com.game.loot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DBConfig {
    @Value("${postgres.url}")
    private String fullUrl;

    @Bean
    public DataSource dataSource() {
        String[] partUrl = fullUrl.split("//")[1].split("@");
        String username = partUrl[0].split(":")[0];
        String password = partUrl[0].split(":")[1];

        String url = "jdbc:postgresql://" + partUrl[1];
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
