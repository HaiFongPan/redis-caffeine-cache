package com.github.hfp.cache;

import org.springframework.data.redis.cache.RedisCacheWriter;

public interface ReffeineCacheWriter extends RedisCacheWriter {

    /**
     * 缓存同步接口
     *
     * @param channel Redis Topic
     * @param message {@link ReffeineCacheMessage}
     */
    void sync(byte[] channel, byte[] message);
}
