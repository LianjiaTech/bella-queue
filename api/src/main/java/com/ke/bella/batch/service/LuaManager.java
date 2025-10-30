package com.ke.bella.batch.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class LuaManager implements ApplicationContextAware {

    private static LuaManager instance;

    private final Map<String, String> scriptContents = new HashMap<>();
    private final Map<JedisPool, Map<String, String>> scriptShaCache = new ConcurrentHashMap<>();

    @PostConstruct
    @SneakyThrows
    private void loadScripts() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String pattern : new String[] { "classpath:lua/*.lua", "classpath:lua/*/*.lua" }) {
            for (Resource resource : resolver.getResources(pattern)) {
                String fileName = resource.getFilename();
                if(fileName == null)
                    continue;

                String module = extractModule(resource);
                String scriptName = fileName.substring(0, fileName.lastIndexOf('.'));
                String scriptKey = module + "_" + scriptName;

                try (InputStream is = resource.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    scriptContents.put(scriptKey, content);
                    log.info("Loaded Lua script: {}", scriptKey);
                }
            }
        }
    }

    public static Object execute(JedisPool jedisPool, String scriptName, List<String> args) {
        return execute(jedisPool, "default", scriptName, Collections.emptyList(), args);
    }

    public static Object execute(JedisPool jedisPool, String module, String scriptName, List<String> args) {
        return execute(jedisPool, module, scriptName, Collections.emptyList(), args);
    }

    public static Object execute(JedisPool jedisPool, String module, String scriptName, List<String> keys, List<String> args) {
        try (Jedis jedis = jedisPool.getResource()) {
            String sha = getScriptSha(jedisPool, module, scriptName);
            String script = getScriptContent(module, scriptName);

            try {
                return Optional.ofNullable(sha)
                        .map(s -> jedis.evalsha(s, keys, args))
                        .orElseGet(() -> jedis.eval(script, keys, args));
            } catch (JedisDataException e) {
                if(isScriptNotFoundError(e)) {
                    clearScriptCache(jedisPool, module, scriptName);
                    return jedis.eval(script, keys, args);
                }
                throw e;
            }
        }
    }

    public static String getScriptSha(JedisPool jedisPool, String module, String scriptName) {
        return instance.doGetScriptSha(jedisPool, module + "_" + scriptName);
    }

    public static String getScriptContent(String module, String scriptName) {
        return instance.doGetScriptContent(module + "_" + scriptName);
    }

    private String doGetScriptContent(String scriptName) {
        return scriptContents.get(scriptName);
    }

    private String doGetScriptSha(JedisPool jedisPool, String scriptName) {
        Map<String, String> shaMap = scriptShaCache.computeIfAbsent(jedisPool, pool -> new ConcurrentHashMap<>());

        return Optional.ofNullable(shaMap.get(scriptName))
                .orElseGet(() -> compileScript(jedisPool, scriptName, shaMap));
    }

    public static void clearScriptCache(JedisPool jedisPool, String module, String scriptName) {
        Optional.ofNullable(instance.scriptShaCache.get(jedisPool))
                .ifPresent(shaMap -> {
                    shaMap.remove(module + "_" + scriptName);
                });
    }

    private String compileScript(JedisPool jedisPool, String scriptName, Map<String, String> shaMap) {
        String content = scriptContents.get(scriptName);
        if(content == null) {
            log.warn("Script content not found: {}", scriptName);
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String sha = jedis.scriptLoad(content);
            shaMap.put(scriptName, sha);
            return sha;
        } catch (Exception e) {
            log.warn("Failed to compile script {}: {}", scriptName, e.getMessage());
            return null;
        }
    }

    private static boolean isScriptNotFoundError(JedisDataException e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("NOSCRIPT") ||
                        message.contains("No matching script") ||
                        message.contains("script not found")
        );
    }

    public enum Module {
        online, offline;
    }

    private String extractModule(Resource resource) {
        try {
            String uri = resource.getURI().toString();
            int luaIndex = uri.lastIndexOf("lua/");
            int lastSlashIndex = uri.lastIndexOf("/");

            if(lastSlashIndex <= luaIndex + 4) {
                return "default";
            }

            return uri.substring(luaIndex + 4, lastSlashIndex);
        } catch (IOException e) {
            log.warn("Failed to extract module from resource: {}", resource);
            return "default";
        }
    }

    @Override
    @SuppressWarnings("all")
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        instance = this;
    }
}
