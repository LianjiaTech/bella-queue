package com.ke.bella.batch;

import com.ke.bella.queue.worker.WorkerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = { R2dbcAutoConfiguration.class }, scanBasePackages = { "com.ke.bella.batch" })
@EnableCaching
@Import(WorkerConfiguration.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
