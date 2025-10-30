package com.ke.bella.batch.configuration;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "apollo.enabled", havingValue = "true")
@EnableApolloConfig
public class ApolloConfig {
}
