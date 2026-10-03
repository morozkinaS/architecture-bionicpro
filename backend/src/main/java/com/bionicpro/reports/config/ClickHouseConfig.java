package com.bionicpro.reports.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Подключение к OLAP-витрине ClickHouse.
 */
@Configuration
public class ClickHouseConfig {

    @Bean
    public DataSource clickHouseDataSource(
            @Value("${clickhouse.url}") String url,
            @Value("${clickhouse.username}") String username,
            @Value("${clickhouse.password}") String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        try {
            return new ClickHouseDataSource(url, props);
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Не удалось создать подключение к ClickHouse: " + url, ex);
        }
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}