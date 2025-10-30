package com.ke.bella.batch;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(TestApplication.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=junit,test" })
public abstract class TestBaseContext {

    public static final String OPENAI_API_KEY = "test-api-key-12345678";

    @BeforeEach
    void setUp() {
        BellaContext.setOperator(Operator.builder()
                .userId(1000000000000000L)
                .userName("Test User")
                .build());
        BellaContext.setApikey(ApikeyInfo.builder().apikey(OPENAI_API_KEY).build());
    }

}

