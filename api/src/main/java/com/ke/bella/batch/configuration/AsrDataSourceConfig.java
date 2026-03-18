package com.ke.bella.batch.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

/**
 * ASR 数据源配置
 * 为 ASR 模块配置独立的数据库连接
 */
@Configuration
@Slf4j
public class AsrDataSourceConfig {

    /**
     * ASR 数据源配置属性
     */
    @Bean
    @ConfigurationProperties(prefix = "bella.asr.datasource.hikari")
    public HikariConfig asrHikariConfig() {
        return new HikariConfig();
    }

    /**
     * ASR 数据源
     */
    @Bean(name = "asrDataSource")
    public DataSource asrDataSource(
            HikariConfig asrHikariConfig,
            org.springframework.core.env.Environment env) {

        // 从 Apollo 配置中心读取数据库连接信息
        // Apollo 中的配置路径：application namespace
        // 配置项：
        //   - bella.asr.datasource.url (或环境变量 BELLA_ASR_DB_URL)
        //   - bella.asr.datasource.username (或环境变量 BELLA_ASR_DB_USERNAME)
        //   - bella.asr.datasource.password (或环境变量 BELLA_ASR_DB_PASSWORD，加密存储)
        //   - bella.asr.datasource.driver-class-name

        String url = env.getProperty("bella.asr.datasource.url");
        String username = env.getProperty("bella.asr.datasource.username");
        String password = env.getProperty("bella.asr.datasource.password");
        String driverClassName = env.getProperty("bella.asr.datasource.driver-class-name");

        // 验证必需配置
        validateConfiguration(url, username, password, driverClassName);

        log.info("Configuring ASR DataSource - url={}, username={}, poolName=ASR-HikariPool",
                url, username);

        // 配置 HikariCP 连接池
        asrHikariConfig.setJdbcUrl(url);
        asrHikariConfig.setUsername(username);
        asrHikariConfig.setPassword(password);
        asrHikariConfig.setDriverClassName(driverClassName);
        asrHikariConfig.setPoolName("ASR-HikariPool");

        HikariDataSource dataSource = new HikariDataSource(asrHikariConfig);

        log.info("ASR DataSource configured successfully - minIdle={}, maxPoolSize={}, connectionTimeout={}ms",
                asrHikariConfig.getMinimumIdle(),
                asrHikariConfig.getMaximumPoolSize(),
                asrHikariConfig.getConnectionTimeout());

        return dataSource;
    }

    /**
     * 验证数据库配置的完整性
     *
     * @throws IllegalArgumentException 如果必需配置缺失
     */
    private void validateConfiguration(String url, String username, String password, String driverClassName) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ASR database URL is not configured. " +
                    "Please set 'bella.asr.datasource.url' or 'BELLA_ASR_DB_URL' in Apollo.");
        }

        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ASR database username is not configured. " +
                    "Please set 'bella.asr.datasource.username' or 'BELLA_ASR_DB_USERNAME' in Apollo.");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ASR database password is not configured. " +
                    "Please set 'bella.asr.datasource.password' or 'BELLA_ASR_DB_PASSWORD' in Apollo. " +
                    "Password should be encrypted in Apollo configuration.");
        }

        if (driverClassName == null || driverClassName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ASR database driver class name is not configured. " +
                    "Please set 'bella.asr.datasource.driver-class-name' in Apollo.");
        }
    }

    /**
     * ASR 事务管理器
     */
    @Bean(name = "asrTransactionManager")
    public DataSourceTransactionManager asrTransactionManager(DataSource asrDataSource) {
        return new DataSourceTransactionManager(asrDataSource);
    }

    /**
     * ASR JOOQ DSLContext
     * 用于 ASR 模块的数据库操作
     */
    @Bean(name = "asrDslContext")
    public DSLContext asrDslContext(DataSource asrDataSource) {
        // 使用事务感知的数据源代理
        TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(asrDataSource);
        DSLContext dslContext = DSL.using(proxy, SQLDialect.MYSQL);

        log.info("ASR DSLContext initialized successfully");

        return dslContext;
    }

}
