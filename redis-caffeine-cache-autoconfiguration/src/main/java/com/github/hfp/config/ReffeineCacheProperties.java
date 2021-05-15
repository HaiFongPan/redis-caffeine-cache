package com.github.hfp.config;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = "reffeine.cache")
public class ReffeineCacheProperties {
    private static final String SPLIT_OPTIONS = ",";
    /**
     * 通知其他服务器同步缓存的 redis channelTopic,不支持 pattern
     */
    private String channel;
    /**
     * 缓存前缀
     */
    private String prefix;
    /**
     * Caffeine 缓存的配置串 {@link CaffeineSpec}
     */
    private String caffeineSpec;
    /**
     * Redis 缓存的 TTL,默认不过期
     */
    private String redisCacheTtl;
    /**
     * 初始化缓存的缓存名字, 程序启动后初始化(默认配置),
     */
    private String initialCaches;
    /**
     * 默认支持 NULL value
     */
    private boolean allowNullValues = true;
    /**
     * 默认支持动态创建cache
     */
    private boolean allowFlightCacheCreation = true;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getCaffeineSpec() {
        return caffeineSpec;
    }

    public void setCaffeineSpec(String caffeineSpec) {
        this.caffeineSpec = caffeineSpec;
    }

    public boolean isAllowNullValues() {
        return allowNullValues;
    }

    public void setAllowNullValues(boolean allowNullValues) {
        this.allowNullValues = allowNullValues;
    }

    public String getRedisCacheTtl() {
        return redisCacheTtl;
    }

    public void setRedisCacheTtl(String redisCacheTtl) {
        this.redisCacheTtl = redisCacheTtl;
    }

    public String getInitialCaches() {
        return initialCaches;
    }

    public void setInitialCaches(String initialCaches) {
        this.initialCaches = initialCaches;
    }

    public boolean isAllowFlightCacheCreation() {
        return allowFlightCacheCreation;
    }

    public void setAllowFlightCacheCreation(boolean allowFlightCacheCreation) {
        this.allowFlightCacheCreation = allowFlightCacheCreation;
    }

    public String[] getInitialCacheNames() {
        if (StringUtils.isEmpty(initialCaches)) {
            return new String[0];
        }

        return initialCaches.split(SPLIT_OPTIONS);
    }

    public Duration getRedisCacheTtlOrDefault() {
        if (StringUtils.isEmpty(this.getRedisCacheTtl())) {
            return Duration.ZERO;
        }

        final long ttlLong = parseDuration("redisCacheTtl", this.redisCacheTtl);
        final TimeUnit ttlUnit = parseTimeUnit("redisCacheTtl", this.redisCacheTtl);
        return Duration.ofNanos(ttlUnit.toNanos(ttlLong));
    }

    private static long parseLong(String key, @Nullable String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(
                    "key %s value was set to %s, must be a long", key, value), e);
        }
    }

    /**
     * Returns a parsed duration value.
     */
    static long parseDuration(String key, @Nullable String value) {
        @SuppressWarnings("NullAway")
        String duration = value.substring(0, value.length() - 1);
        return parseLong(key, duration);
    }

    /**
     * Returns a parsed {@link TimeUnit} value.
     */
    static TimeUnit parseTimeUnit(String key, @Nullable String value) {
        @SuppressWarnings("NullAway")
        char lastChar = Character.toLowerCase(value.charAt(value.length() - 1));
        switch (lastChar) {
            case 'd':
                return TimeUnit.DAYS;
            case 'h':
                return TimeUnit.HOURS;
            case 'm':
                return TimeUnit.MINUTES;
            case 's':
                return TimeUnit.SECONDS;
            default:
                throw new IllegalArgumentException(String.format(
                        "key %s invalid format; was %s, must end with one of [dDhHmMsS]", key, value));
        }
    }
}
