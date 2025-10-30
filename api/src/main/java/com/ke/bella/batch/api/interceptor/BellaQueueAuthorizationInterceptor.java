package com.ke.bella.batch.api.interceptor;

import com.ke.bella.batch.utils.OpenapiUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class BellaQueueAuthorizationInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response
            , @NotNull Object handler) {

        String authorization = getHeaderInfo(request, HttpHeaders.AUTHORIZATION);
        if(authorization == null || !authorization.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        String ak = authorization.substring(7);

        ApikeyInfo apikeyInfo = OpenapiUtils.getInstance().whoami(ak);
        if(apikeyInfo == null) {
            log.warn("Invalid API key: {}", ak);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        BellaContext.setApikey(ApikeyInfo.builder().apikey(ak).build());
        BellaContext.setOperator(Operator.builder()
                .userId(apikeyInfo.getUserId())
                .userName(apikeyInfo.getOwnerName())
                .build());

        return true;

    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response
            , @NotNull Object handler, Exception ex) {
        BellaContext.clearAll();
    }

    private String getHeaderInfo(HttpServletRequest request, String key) {
        String string = request.getHeader(key);
        if(StringUtils.isEmpty(string)) {
            string = request.getParameter(key);
        }
        return string;
    }
}
