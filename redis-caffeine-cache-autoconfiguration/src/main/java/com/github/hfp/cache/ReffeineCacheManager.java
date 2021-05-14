package com.github.hfp.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.hfp.config.ReffeineCacheConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReffeineCacheManager extends AbstractCacheManager {
    private static final Pattern NAME_TTL_PATTERN = Pattern.compile("#L(\\d+\\w)#R(\\d+\\w)");

    /**
     * Caffeine 缓存builder, 通过 ReffeineCacheConfiguration#caffeineSpec 初始化
     */
    private Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder();
    /**
     * Redis 客户端
     */
    private final ReffeineCacheWriter reffeineCacheWriter;
    /**
     * 默认的缓存配置
     */
    private final ReffeineCacheConfiguration defaultCacheConfig;
    /**
     * 初始化缓存配置
     */
    private final Map<String, ReffeineCacheConfiguration> initialCacheConfig;
    /**
     * 是否允许新建缓存, {@literal false} 的时候, 如果 initialCacheConfig 没有配置则无法创建缓存
     */
    private final boolean allowInFlightCacheCreation;

    public ReffeineCacheManager(ReffeineCacheWriter reffeineCacheWriter,
                                ReffeineCacheConfiguration defaultCacheConfig,
                                Map<String, ReffeineCacheConfiguration> initialCacheConfig, boolean allowInFlightCacheCreation) {
        Assert.notNull(reffeineCacheWriter, "ReffeineCacheWriter must not be null!");
        Assert.notNull(defaultCacheConfig, "ReffeineCacheConfiguration must not be null!");
        Assert.notNull(initialCacheConfig, "InitialCacheConfig must not be null!");

        this.reffeineCacheWriter = reffeineCacheWriter;
        this.defaultCacheConfig = defaultCacheConfig;
        this.initialCacheConfig = initialCacheConfig;
        this.allowInFlightCacheCreation = allowInFlightCacheCreation;
        if (this.defaultCacheConfig.getCaffeineSpec() != null) {
            caffeineBuilder = Caffeine.from(this.defaultCacheConfig.getCaffeineSpec());
        }
    }

    public ReffeineCacheManager(ReffeineCacheWriter reffeineCacheWriter,
                                ReffeineCacheConfiguration defaultCacheConfig) {
        this(reffeineCacheWriter, defaultCacheConfig, new LinkedHashMap<>(), true);
    }

    public ReffeineCacheManager(ReffeineCacheWriter reffeineCacheWriter,
                                ReffeineCacheConfiguration defaultCacheConfig, boolean allowInFlightCacheCreation) {
        this(reffeineCacheWriter, defaultCacheConfig, new LinkedHashMap<>(), allowInFlightCacheCreation);
    }

    public ReffeineCacheManager(ReffeineCacheWriter reffeineCacheWriter,
                                ReffeineCacheConfiguration defaultCacheConfig,
                                Map<String, ReffeineCacheConfiguration> initialCacheConfig) {
        this(reffeineCacheWriter, defaultCacheConfig, initialCacheConfig, true);
    }

    private boolean isAllowNullValue() {
        return this.defaultCacheConfig.getAllowCacheNullValues();
    }

    @Override
    protected Collection<? extends Cache> loadCaches() {
        Set<ReffeineCache> reffeineCaches = new LinkedHashSet<>(initialCacheConfig.size());
        for (Map.Entry<String, ReffeineCacheConfiguration> entry : initialCacheConfig.entrySet()) {
            reffeineCaches.add(createReffeineCache(entry.getKey(), entry.getValue()));
        }
        return reffeineCaches;
    }

    @Override
    protected Cache getMissingCache(String name) {
        return this.allowInFlightCacheCreation ? createReffeineCache(name,
                initialCacheConfig.getOrDefault(name, defaultCacheConfig)) : null;
    }

    private ReffeineCache createReffeineCache(String name, ReffeineCacheConfiguration configuration) {
        Caffeine<Object, Object> caffeine = caffeineBuilder;
        if (null != configuration.getCaffeineSpec()) {
            caffeine = Caffeine.from(configuration.getCaffeineSpec());
        }
        // ttl by name
        Matcher matcher = NAME_TTL_PATTERN.matcher(name);
        if (matcher.find()) {
            caffeine.expireAfterWrite(parseDuration(name, matcher.group(1)));
            configuration = configuration.redisttl(parseDuration(name, matcher.group(2)));
        }

        return new ReffeineCache(isAllowNullValue(), name, reffeineCacheWriter, configuration, caffeine.build());
    }

    public Caffeine<Object, Object> getCaffeineBuilder() {
        return caffeineBuilder;
    }

    public void setCaffeineBuilder(
            Caffeine<Object, Object> caffeineBuilder) {
        this.caffeineBuilder = caffeineBuilder;
    }

    public ReffeineCacheWriter getReffeineCacheWriter() {
        return reffeineCacheWriter;
    }

    public ReffeineCacheConfiguration getDefaultCacheConfig() {
        return defaultCacheConfig;
    }

    public Map<String, ReffeineCacheConfiguration> getInitialCacheConfig() {
        return initialCacheConfig;
    }

    public boolean isAllowInFlightCacheCreation() {
        return allowInFlightCacheCreation;
    }

    static Duration parseDuration(String name, String value) {
        String duration = value.substring(0, value.length() - 1);
        Long amount = Long.valueOf(duration);
        ChronoUnit timeUnit;
        char lastChar = Character.toLowerCase(value.charAt(value.length() - 1));
        switch (lastChar) {
            case 'd':
                timeUnit = ChronoUnit.DAYS;
                break;
            case 'h':
                timeUnit = ChronoUnit.HOURS;
                break;
            case 'm':
                timeUnit = ChronoUnit.MINUTES;
                break;
            case 's':
                timeUnit = ChronoUnit.SECONDS;
                break;
            default:
                throw new IllegalArgumentException(String.format(
                        "name %s invalid format; was %s, must end with one of [dDhHmMsS]", name, value));
        }

        return Duration.of(amount, timeUnit);
    }

    /**
     * CacheManagerBuilder
     */
    public static class ReffeineCacheManagerBuilder {
        private ReffeineCacheWriter reffeineCacheWriter;
        private ReffeineCacheConfiguration defaultCacheConfig = ReffeineCacheConfiguration.defaultCacheConfig();
        private Map<String, ReffeineCacheConfiguration> initialCacheConfig = new LinkedHashMap<>();
        private boolean allowInFlightCacheCreation = true;
        private String[] initialCacheNames;

        private ReffeineCacheManagerBuilder(ReffeineCacheWriter reffeineCacheWriter) {
            this.reffeineCacheWriter = reffeineCacheWriter;
        }

        public static ReffeineCacheManagerBuilder fromReffeineCacheWriter(ReffeineCacheWriter reffeineCacheWriter) {
            Assert.notNull(reffeineCacheWriter, "ReffeineCacheWriter must not be null!");
            return new ReffeineCacheManagerBuilder(reffeineCacheWriter);
        }

        public static ReffeineCacheManagerBuilder fromConnectionFactory(RedisConnectionFactory redisConnectionFactory) {
            Assert.notNull(redisConnectionFactory, "RedisConnectionFactory must not be null!");
            return new ReffeineCacheManagerBuilder(new DefaultReffeineCacheWriter(redisConnectionFactory));
        }

        public ReffeineCacheManagerBuilder defaultCacheConfig(ReffeineCacheConfiguration reffeineCacheConfiguration) {
            Assert.notNull(defaultCacheConfig, "ReffeineCacheConfiguration must not be null!");
            this.defaultCacheConfig = reffeineCacheConfiguration;
            return this;
        }

        public ReffeineCacheManagerBuilder allowFlightCacheCreation(boolean allowInFlightCacheCreation) {
            this.allowInFlightCacheCreation = allowInFlightCacheCreation;
            return this;
        }

        public ReffeineCacheManagerBuilder initialCaches(String[] initialCacheNames) {
            this.initialCacheNames = initialCacheNames;
            return this;
        }

        public ReffeineCacheManagerBuilder initialCacheConfig(
                Map<String, ReffeineCacheConfiguration> initialCacheConfig) {
            this.initialCacheConfig = initialCacheConfig;
            return this;
        }

        public ReffeineCacheManager build() {
            Map<String, ReffeineCacheConfiguration> initConfigs = new LinkedHashMap<>();
            if (initialCacheNames != null && initialCacheNames.length > 0) {
                for (String name : initialCacheNames) {
                    initConfigs.put(name, defaultCacheConfig);
                }
            }

            if (initialCacheConfig != null && initialCacheConfig.size() > 0) {
                initConfigs.putAll(initialCacheConfig);
            }

            return new ReffeineCacheManager(reffeineCacheWriter, defaultCacheConfig, initConfigs,
                    allowInFlightCacheCreation);
        }
    }
}
