package com.ke.bella.batch;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceTransactionManagerAutoConfiguration.class },
        scanBasePackages = { "com.ke.bella.batch" })
public class TestApplication {
    // This class is intentionally left empty. It serves as a configuration class for the Spring Boot application.
}
