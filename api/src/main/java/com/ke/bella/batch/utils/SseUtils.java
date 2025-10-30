package com.ke.bella.batch.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;

@Slf4j
public class SseUtils {

    public static SseEmitter createSse(long timeout, String reqId) {
        SseEmitter sse = new SseEmitter(timeout);

        sse.onCompletion(() -> log.info("[{}] 结束连接...................", reqId));
        sse.onTimeout(() -> log.info("[{}]连接超时...................", reqId));
        sse.onError(e -> log.info("[{}]连接异常,{}", reqId, e.toString()));
        log.info("[{}]创建sse连接成功！", reqId);

        return sse;
    }

    public static void send(SseEmitter sse, SseEventBuilder event) {
        try {
            sse.send(event);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

}
