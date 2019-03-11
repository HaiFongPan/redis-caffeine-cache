package com.github.hfp.cache;

import com.github.hfp.util.IPUtil;
import java.util.Objects;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.util.SerializationUtils;

public class ReffeineCacheMessageListener implements MessageListener {
    private final Log LOGGER = LogFactory.getLog(getClass());
    private ReffeineCacheManager reffeineCacheManager;

    public ReffeineCacheMessageListener(ReffeineCacheManager reffeineCacheManager) {
        this.reffeineCacheManager = reffeineCacheManager;
    }

    /**
     * 监听同步本地缓存消息
     * @param message Redis 消息主题
     * @param pattern 消息 Topic
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        ReffeineCacheMessage reffeineCacheMessage =
                (ReffeineCacheMessage) SerializationUtils.deserialize(message.getBody());
        if (reffeineCacheMessage == null) {
            LOGGER.warn("ReffeineCache onMessage error, reffeineCacheMessage is null");
            return;
        }

        if (Objects.equals(reffeineCacheMessage.getSource(), IPUtil.getIP())) {
            LOGGER.debug("ReffeineCache onMessage warning, skip own message");
            return;
        }

        final ReffeineCache cache = (ReffeineCache) reffeineCacheManager.getCache(reffeineCacheMessage.getName());
        if (cache != null) {
            LOGGER.info("ReffeineCache start clear local cache for key " + reffeineCacheMessage.getKey());
            cache.clearLocal((String) reffeineCacheMessage.getKey());
        }
    }
}
