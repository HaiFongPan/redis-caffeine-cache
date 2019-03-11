package com.github.hfp.config;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.util.Assert;

public class ReffeineCacheConfiguration {

    private static final String DEFAULT_CACHE_EVICT_CHANNEL = "redis:caffeine:sync:channel";
    /**
     * Redis 缓存过期时间, 默认永久
     */
    private final Duration redisttl;
    /**
     * 是否允许Null值
     */
    private final boolean cacheNullValues;
    /**
     * 前缀计算
     */
    private final CacheKeyPrefix keyPrefix;

    private final RedisSerializationContext.SerializationPair<String> keySerializationPair;
    private final RedisSerializationContext.SerializationPair<Object> valueSerializationPair;

    private final ConversionService conversionService;
    /**
     * Caffeine 缓存配置
     */
    private final CaffeineSpec caffeineSpec;
    /**
     * 同步缓存Redis通道
     */
    private final String cacheEvictChannel;

    @SuppressWarnings("unchecked")
    private ReffeineCacheConfiguration(Duration ttl, Boolean cacheNullValues,
            CacheKeyPrefix keyPrefix, RedisSerializationContext.SerializationPair<String> keySerializationPair,
            RedisSerializationContext.SerializationPair<?> valueSerializationPair, ConversionService conversionService,
            CaffeineSpec caffeineSpec, String cacheClearEvictChannel) {

        this.redisttl = ttl;
        this.cacheNullValues = cacheNullValues;
        this.keyPrefix = keyPrefix;
        this.keySerializationPair = keySerializationPair;
        this.valueSerializationPair = (RedisSerializationContext.SerializationPair<Object>) valueSerializationPair;
        this.conversionService = conversionService == null? new DefaultFormattingConversionService() : conversionService;

        this.caffeineSpec = caffeineSpec;
        this.cacheEvictChannel = cacheClearEvictChannel;
    }

    public static ReffeineCacheConfiguration defaultCacheConfig() {

        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        registerDefaultConverters(conversionService);

        return new ReffeineCacheConfiguration(Duration.ZERO, true, CacheKeyPrefix.simple(),
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()),
                RedisSerializationContext.SerializationPair.fromSerializer(new JdkSerializationRedisSerializer()),
                conversionService,
                null, DEFAULT_CACHE_EVICT_CHANNEL);
    }

    ReffeineCacheConfiguration redisttl(Duration ttl) {

        Assert.notNull(ttl, "TTL duration must not be null!");

        return new ReffeineCacheConfiguration(ttl, cacheNullValues, keyPrefix, keySerializationPair,
                valueSerializationPair, conversionService, caffeineSpec, cacheEvictChannel);
    }

    ReffeineCacheConfiguration prefixKeysWith(String prefix) {

        Assert.notNull(prefix, "Key Prefix must not be null!");

        return computePrefixWith((cacheName) -> prefix.concat(":").concat(cacheName));
    }

    private ReffeineCacheConfiguration computePrefixWith(CacheKeyPrefix cacheKeyPrefix) {

        Assert.notNull(cacheKeyPrefix, "Function for computing key prefix must not be null!");

        return new ReffeineCacheConfiguration(redisttl, cacheNullValues, cacheKeyPrefix, keySerializationPair,
                valueSerializationPair, conversionService, caffeineSpec, cacheEvictChannel);
    }

    public ReffeineCacheConfiguration disableCachingNullValues() {
        return new ReffeineCacheConfiguration(redisttl, false, keyPrefix, keySerializationPair,
                valueSerializationPair,
                conversionService, caffeineSpec, cacheEvictChannel);
    }

    public ReffeineCacheConfiguration withConversionService(ConversionService conversionService) {

        Assert.notNull(conversionService, "ConversionService must not be null!");

        return new ReffeineCacheConfiguration(redisttl, cacheNullValues, keyPrefix, keySerializationPair,
                valueSerializationPair, conversionService, caffeineSpec, cacheEvictChannel);
    }

    public ReffeineCacheConfiguration serializeKeysWith(
            RedisSerializationContext.SerializationPair<String> keySerializationPair) {

        Assert.notNull(keySerializationPair, "KeySerializationPair must not be null!");

        return new ReffeineCacheConfiguration(redisttl, cacheNullValues, keyPrefix, keySerializationPair,
                valueSerializationPair, conversionService, caffeineSpec, cacheEvictChannel);
    }

    public ReffeineCacheConfiguration serializeValuesWith(
            RedisSerializationContext.SerializationPair<?> valueSerializationPair) {

        Assert.notNull(valueSerializationPair, "ValueSerializationPair must not be null!");

        return new ReffeineCacheConfiguration(redisttl, cacheNullValues, keyPrefix, keySerializationPair,
                valueSerializationPair, conversionService, caffeineSpec, cacheEvictChannel);
    }

    ReffeineCacheConfiguration caffeineSpec(CaffeineSpec caffeineSpec) {
        Assert.notNull(caffeineSpec, "CaffeineSpec must not be null!");

        return new ReffeineCacheConfiguration(redisttl, cacheNullValues, keyPrefix, keySerializationPair,
                valueSerializationPair, conversionService, caffeineSpec, cacheEvictChannel);
    }

    ReffeineCacheConfiguration cacheEvictChannel(String cacheEvictChannel) {
        Assert.notNull(cacheEvictChannel, "CacheEvictChannel must not be null!");
        return new ReffeineCacheConfiguration(redisttl, cacheNullValues, keyPrefix, keySerializationPair,
                valueSerializationPair, conversionService, caffeineSpec, cacheEvictChannel);
    }

    public String getKeyPrefixFor(String cacheName) {

        Assert.notNull(cacheName, "Cache name must not be null!");

        return keyPrefix.compute(cacheName);
    }

    public boolean getAllowCacheNullValues() {
        return cacheNullValues;
    }

    public RedisSerializationContext.SerializationPair<String> getKeySerializationPair() {
        return keySerializationPair;
    }

    public RedisSerializationContext.SerializationPair<Object> getValueSerializationPair() {
        return valueSerializationPair;
    }

    public Duration getRedisttl() {
        return redisttl;
    }

    public ConversionService getConversionService() {
        return conversionService;
    }

    public CaffeineSpec getCaffeineSpec() {
        return caffeineSpec;
    }

    public String getCacheEvictChannel() {
        return cacheEvictChannel;
    }

    private static void registerDefaultConverters(ConverterRegistry registry) {

        Assert.notNull(registry, "ConverterRegistry must not be null!");

        registry.addConverter(String.class, byte[].class, source -> source.getBytes(StandardCharsets.UTF_8));
        registry.addConverter(SimpleKey.class, String.class, SimpleKey::toString);
    }
}
