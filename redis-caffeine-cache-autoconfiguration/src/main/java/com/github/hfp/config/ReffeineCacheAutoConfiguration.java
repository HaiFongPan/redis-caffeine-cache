package com.github.hfp.config;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.github.hfp.cache.DefaultReffeineCacheWriter;
import com.github.hfp.cache.ReffeineCacheManager;
import com.github.hfp.cache.ReffeineCacheMessageListener;
import com.github.hfp.cache.ReffeineCacheWriter;
import com.github.hfp.util.IPUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ReffeineCacheProperties.class)
public class ReffeineCacheAutoConfiguration {
    private final Log LOGGER = LogFactory.getLog(getClass());
    @Autowired
    private ReffeineCacheProperties properties;

    @ConditionalOnMissingBean(ReffeineCacheWriter.class)
    @Bean
    public ReffeineCacheWriter reffeineCacheWriter(RedisConnectionFactory connectionFactory) {
        return new DefaultReffeineCacheWriter(connectionFactory, Duration.ofMillis(50));
    }

    @ConditionalOnMissingBean(ReffeineCacheConfiguration.class)
    @Bean
    public ReffeineCacheConfiguration reffeineCacheConfiguration() {
        ReffeineCacheConfiguration configuration = ReffeineCacheConfiguration.defaultCacheConfig();
        if (!StringUtils.isEmpty(properties.getPrefix())) {
            configuration = configuration.prefixKeysWith(properties.getPrefix());
        }

        if (!StringUtils.isEmpty(properties.getChannel())) {
            configuration = configuration.cacheEvictChannel(properties.getChannel());
        }

        if (!StringUtils.isEmpty(properties.getCaffeineSpec())) {
            configuration = configuration.caffeineSpec(CaffeineSpec.parse(properties.getCaffeineSpec()));
        }

        if (!StringUtils.isEmpty(properties.getRedisCacheTtl())) {
            configuration = configuration.redisttl(properties.getRedisCacheTtlOrDefault());
        }
        return configuration;
    }

    @ConditionalOnMissingBean(ReffeineCacheManager.class)
    @Bean
    public ReffeineCacheManager reffeineCacheManager(ReffeineCacheWriter cacheWriter,
                                                     ReffeineCacheConfiguration cacheConfiguration) {
        return ReffeineCacheManager.ReffeineCacheManagerBuilder.fromReffeineCacheWriter(cacheWriter)
                .initialCaches(properties.getInitialCacheNames())
                .allowFlightCacheCreation(properties.isAllowFlightCacheCreation())
                .defaultCacheConfig(cacheConfiguration)
                .build();
    }

    @ConditionalOnMissingBean(name = {"redisMessageListenerTaskExecutor"})
    @Bean(value = "redisMessageListenerTaskExecutor")
    public ThreadPoolTaskExecutor redisMessageListenerTaskExecutor() {
        ThreadPoolTaskExecutor springSessionRedisTaskExecutor = new ThreadPoolTaskExecutor();
        //核心线程
        springSessionRedisTaskExecutor.setCorePoolSize(5);
        //最大线程
        springSessionRedisTaskExecutor.setMaxPoolSize(300);
        //线程最大空闲时间
        springSessionRedisTaskExecutor.setKeepAliveSeconds(10);
        //队列大小
        springSessionRedisTaskExecutor.setQueueCapacity(1000);
        //线程名称前缀
        springSessionRedisTaskExecutor.setThreadNamePrefix("RedisListener-");
        return springSessionRedisTaskExecutor;
    }

    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       ReffeineCacheManager reffeineCacheManager,
                                                                       @Autowired @Qualifier("redisMessageListenerTaskExecutor") ThreadPoolTaskExecutor redisMessageListenerTaskExecutor) {
        final ReffeineCacheConfiguration defaultCacheConfig = reffeineCacheManager.getDefaultCacheConfig();
        final ChannelTopic channelTopic = new ChannelTopic(defaultCacheConfig.getCacheEvictChannel());
        LOGGER.info("IP : " + IPUtil.getIP() + " start listen on " + defaultCacheConfig.getCacheEvictChannel());
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(redisMessageListenerTaskExecutor);
        container.addMessageListener(new ReffeineCacheMessageListener(reffeineCacheManager), channelTopic);
        return container;
    }
}
