package com.ke.bella.batch.configuration;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * RestTemplate 配置
 * 为 ASR 模块配置带连接池的 HTTP 客户端
 */
@Configuration
@Slf4j
public class RestTemplateConfig {

    /**
     * ASR 专用的 RestTemplate
     * 配置连接池、超时、长连接保持等
     */
    @Bean(name = "asrRestTemplate")
    public RestTemplate asrRestTemplate() {
        // 1. 配置连接池
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();

        // 最大总连接数
        connectionManager.setMaxTotal(200);

        // 每个路由（域名）的最大连接数
        connectionManager.setDefaultMaxPerRoute(50);

        // 连接在池中的最大生存时间（30秒）
        connectionManager.setValidateAfterInactivity(30000);

        // 2. 配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
                // 从连接池获取连接的超时时间（5秒）
                .setConnectionRequestTimeout(5000)
                // 建立连接的超时时间（5秒）
                .setConnectTimeout(5000)
                // Socket 读取超时时间（30秒）
                .setSocketTimeout(30000)
                .build();

        // 3. 构建 HttpClient
        HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // 连接保持存活时间（30秒）
                .setConnectionTimeToLive(30, TimeUnit.SECONDS)
                // 自动重试（针对幂等请求）
                .setRetryHandler((exception, executionCount, context) -> {
                    // 最多重试1次
                    if (executionCount > 1) {
                        return false;
                    }
                    // 仅对 IOException 重试
                    return exception instanceof java.io.IOException;
                })
                .build();

        // 4. 创建 RestTemplate
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);

        log.info("ASR RestTemplate configured - " +
                "maxTotal={}, maxPerRoute={}, " +
                "connectTimeout={}ms, readTimeout={}ms",
                200, 50, 5000, 30000);

        return restTemplate;
    }
}
