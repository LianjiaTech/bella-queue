package com.ke.bella.batch.configuration;

import com.ke.bella.batch.RedisMesh;
import com.ke.bella.batch.TaskExecutor;
import com.ke.bella.batch.db.IDGenerator;
import com.ke.bella.batch.db.repo.InstanceRepo;
import com.ke.bella.batch.service.BatchCompleteCountUpdater;
import com.ke.bella.batch.service.BatchService;
import com.ke.bella.batch.service.QueueHeadUpdater;
import com.ke.bella.batch.service.QueueTaskCountUpdater;
import com.ke.bella.batch.utils.OpenapiUtils;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import com.ke.bella.openapi.server.BellaService;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

@Slf4j
@Configuration
@BellaService
public class BellaAutoConf {

    @Resource
    private InstanceRepo instanceRepo;
    @Resource
    private QueueTaskCountUpdater queueTaskCountUpdater;
    @Resource
    private BatchCompleteCountUpdater batchCompleteCountUpdater;
    @Resource
    private QueueHeadUpdater queueHeadUpdater;

    private RedisMesh redisMesh;

    @Autowired
    @Lazy
    private OpenapiClient openapiClient;

    @Autowired
    @Lazy
    private OpenAiServiceFactory openAiServiceFactory;
    @Resource
    private BatchService bs;

    @PostConstruct
    public void postConstruct() {
        OpenapiUtils.initialize(openapiClient, openAiServiceFactory);
        Long id = instanceRepo.register(BellaServerContextHolder.getIp(), BellaServerContextHolder.getPort());
        IDGenerator.setInstanceId(id);
    }

    @Bean
    public JedisPool jedisPool(@Value("${bella.queue.redis.host}") String host,
            @Value("${bella.queue.redis.port}") int port,
            @Value("${bella.queue.redis.user:#{null}}") String user,
            @Value("${bella.queue.redis.password:#{null}}") String pwd) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setJmxEnabled(false);
        return new JedisPool(config, host, port, user, pwd);
    }

    @Bean
    public RedisMesh redisMesh(JedisPool pool, @Value("${spring.profiles.active}") String profile) {
        String key = String.format("%s:%s", BellaServerContextHolder.getIp(), BellaServerContextHolder.getPort());
        RedisMesh mesh = new RedisMesh(profile, key, "bella-queue", pool);
        mesh.start();
        this.redisMesh = mesh;
        return mesh;
    }

    @Bean
    public OpenAiService openAiService(OpenapiProperties openapiProperties) {
        return openAiServiceFactory.create(openapiProperties.getServiceAk());
    }

    @Bean
    public com.ke.bella.queue.worker.TaskExecutor workerTaskExecutor() {
        return task -> bs.split(task);
    }

    @PreDestroy
    public void gracefulShutdown() {
        try {
            batchCompleteCountUpdater.flush();
            queueTaskCountUpdater.flush();
            queueHeadUpdater.flush();
            redisMesh.shutdown();
            TaskExecutor.gracefulShutdown(60);
            instanceRepo.unregister(BellaServerContextHolder.getIp(), BellaServerContextHolder.getPort());
        } catch (InterruptedException e) {
            log.error("BellaAutoConf shutdown interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

}
