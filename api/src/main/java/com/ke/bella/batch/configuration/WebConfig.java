package com.ke.bella.batch.configuration;

import com.ke.bella.batch.api.interceptor.BellaQueueAuthorizationInterceptor;
import com.ke.bella.openapi.BellaContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private BellaQueueAuthorizationInterceptor authorizationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/v1/batches/**")
                .addPathPatterns("/v1/queue/**")
                .addPathPatterns("/v1/console/**");
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> traceIdFilter() {
        FilterRegistrationBean<OncePerRequestFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NotNull HttpServletRequest request
                    , @NotNull HttpServletResponse response
                    , @NotNull FilterChain filterChain)
                    throws ServletException, IOException {
                try {
                    String bellaTraceId = BellaContext.getTraceId();
                    MDC.put("bellaTraceId", bellaTraceId);
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.clear();
                }
            }
        });
        filterRegistrationBean.setOrder(2);
        return filterRegistrationBean;
    }
}
