package com.github.hfp.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.hfp.config.ReffeineCacheConfiguration;
import com.github.hfp.util.IPUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.util.ByteUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.SerializationUtils;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

public class ReffeineCache extends AbstractValueAdaptingCache {
    private final Log LOGGER = LogFactory.getLog(getClass());
    private static final byte[] BINARY_NULL_VALUE = new JdkSerializationRedisSerializer().serialize(NullValue.INSTANCE);
    /**
     * 缓存名字
     */
    private final String name;
    /**
     * 基于 RedisCacheWriter 的定制 Redis 客户端
     */
    private final ReffeineCacheWriter reffeineCacheWriter;
    /**
     * 整个缓存的配置
     */
    private final ReffeineCacheConfiguration cacheConfig;
    /**
     * 序列化方式
     */
    private final ConversionService conversionService;
    /**
     * Caffeine 缓存实际上在这里
     */
    private final Cache<Object, Object> localCache;
    /**
     * 用于同步消息时 Key 的序列化
     */
    private final StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
    /**
     * 同步消息时的 redis topic
     */
    private byte[] cacheSyncChannel;

    public ReffeineCache(boolean allowNullValues, String name,
                         ReffeineCacheWriter reffeineCacheWriter, ReffeineCacheConfiguration cacheConfig,
                         Cache<Object, Object> localCache) {
        super(allowNullValues);
        this.name = name;
        this.reffeineCacheWriter = reffeineCacheWriter;
        this.cacheConfig = cacheConfig;
        this.localCache = localCache;
        this.conversionService = cacheConfig.getConversionService();
        this.cacheSyncChannel = stringRedisSerializer.serialize(cacheConfig.getCacheEvictChannel());
    }

    /**
     * 查找缓存
     * 1. 查找本地缓存, 命中则返回
     * 2. 本地没有则查找 redis
     */
    @Override
    protected Object lookup(Object key) {
        final String localCacheKey = createCacheKey(key);
        final byte[] redisCacheKey = serializeCacheKey(localCacheKey);
        // lookup caffeine first
        LOGGER.info("look update cache key " + key + " from caffeine");
        Object value = localCache.getIfPresent(localCacheKey);
        if (value == null) {
            // if null lookup redis
            value = reffeineCacheWriter.get(name, redisCacheKey);
            LOGGER.info("look update cache key " + key + " from redis");
            if (value != null) {
                localCache.put(localCacheKey, value);
            }
        }

        return value == null ? null : deserializeCacheValue((byte[]) value);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this.reffeineCacheWriter;
    }

    /**
     * 获取数据, 缓存中拿不到则通过 valueLoader 获取
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        final ValueWrapper valueWrapper = get(key);
        if (valueWrapper != null) {
            return (T) valueWrapper.get();
        }

        final T value = valueFromLoader(key, valueLoader);
        put(key, value);
        return value;
    }

    /**
     * 更新缓存, 同步消息值其他机器, 清除多机缓存
     */
    @Override
    public void put(Object key, Object value) {
        final Object cacheValue = preProcessCacheValue(value);
        if (!isAllowNullValues() && cacheValue == null) {
            throw new IllegalArgumentException(String.format(
                    "Cache '%s' does not allow 'null' values. Avoid storing null via '@Cacheable(unless=\"#result == null\")' or configure ReffeineCache to allow 'null' via ReffeineCacheConfiguration.",
                    name));
        }
        final String localCacheKey = createCacheKey(key);
        final byte[] redisCacheKey = serializeCacheKey(localCacheKey);
        final byte[] serializeCacheValue = serializeCacheValue(cacheValue);
        reffeineCacheWriter.put(name, redisCacheKey, serializeCacheValue, cacheConfig.getRedisttl());
        reffeineCacheWriter.sync(cacheSyncChannel, serializeCacheMessage(localCacheKey));
        localCache.put(localCacheKey, serializeCacheValue);
    }

    /**
     * 更新缓存, 当缓存中不存在的时候
     */
    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        final Object cacheValue = preProcessCacheValue(value);
        if (!isAllowNullValues() && cacheValue == null) {
            return get(key);
        }
        final String localCacheKey = createCacheKey(key);
        final byte[] redisCacheKey = serializeCacheKey(localCacheKey);
        final byte[] serializeCacheValue = serializeCacheValue(value);
        final byte[] result =
                reffeineCacheWriter.putIfAbsent(name, redisCacheKey, serializeCacheValue, cacheConfig.getRedisttl());
        if (result == null) {
            // key does not already exist. renew localCache.
            reffeineCacheWriter.sync(cacheSyncChannel, serializeCacheMessage(localCacheKey));
            localCache.put(localCacheKey, serializeCacheValue);
            return null;
        }

        return new SimpleValueWrapper(fromStoreValue(deserializeCacheValue(result)));
    }

    /**
     * 清除缓存, 同步消息 Key 为需要清除的缓存
     */
    @Override
    public void evict(Object key) {
        final String localCacheKey = createCacheKey(key);
        final byte[] redisCacheKey = serializeCacheKey(localCacheKey);
        reffeineCacheWriter.remove(name, redisCacheKey);
        reffeineCacheWriter.sync(cacheSyncChannel, serializeCacheMessage(localCacheKey));
        localCache.invalidate(localCacheKey);
    }

    /**
     * 清除所有缓存, 同步消息 Key 为 {@literal null}
     */
    @Override
    public void clear() {
        final byte[] cacheKey = createAndConvertCacheKey("*");
        reffeineCacheWriter.clean(name, cacheKey);
        reffeineCacheWriter.sync(cacheSyncChannel, serializeCacheMessage(null));
        localCache.invalidateAll();
    }

    /**
     * 清除本地缓存
     */
    void clearLocal(String cacheKey) {
        if (cacheKey == null) {
            localCache.invalidateAll();
        } else {
            localCache.invalidate(cacheKey);
        }
    }

    /**
     * 构建缓存 Key, 对象转字符串, 拼接上自定义前缀和缓存名
     */
    private String createCacheKey(Object key) {
        String convertedKey = convertKey(key);
        return cacheConfig.getKeyPrefixFor(name).concat(":").concat(convertedKey);
    }

    private String convertKey(Object key) {
        TypeDescriptor source = TypeDescriptor.forObject(key);
        if (conversionService.canConvert(source, TypeDescriptor.valueOf(String.class))) {
            return conversionService.convert(key, String.class);
        }
        Method toString = ReflectionUtils.findMethod(key.getClass(), "toString");
        if (toString != null && !Object.class.equals(toString.getDeclaringClass())) {
            return key.toString();
        }
        throw new IllegalStateException(
                String.format("Cannot convert %s to String. Register a Converter or override toString().", source));
    }

    private byte[] serializeCacheKey(String cacheKey) {
        return ByteUtils.getBytes(cacheConfig.getKeySerializationPair().write(cacheKey));
    }

    private byte[] serializeCacheValue(Object value) {

        if (isAllowNullValues() && value instanceof NullValue) {
            return BINARY_NULL_VALUE;
        }

        return ByteUtils.getBytes(cacheConfig.getValueSerializationPair().write(value));
    }

    private Object deserializeCacheValue(byte[] value) {

        if (isAllowNullValues() && ObjectUtils.nullSafeEquals(value, BINARY_NULL_VALUE)) {
            return NullValue.INSTANCE;
        }

        return cacheConfig.getValueSerializationPair().read(ByteBuffer.wrap(value));
    }

    private byte[] createAndConvertCacheKey(Object key) {
        return serializeCacheKey(createCacheKey(key));
    }

    private static <T> T valueFromLoader(Object key, Callable<T> valueLoader) {

        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    private byte[] serializeCacheMessage(String key) {
        LOGGER.warn("client publish sync message for cache: " + key + " " + IPUtil.getIP());
        final ReffeineCacheMessage reffeineCacheMessage = new ReffeineCacheMessage(name, key, IPUtil.getIP());
        return SerializationUtils.serialize(reffeineCacheMessage);
    }

    private Object preProcessCacheValue(@Nullable Object value) {

        if (value != null) {
            return value;
        }

        return isAllowNullValues() ? NullValue.INSTANCE : null;
    }
}
