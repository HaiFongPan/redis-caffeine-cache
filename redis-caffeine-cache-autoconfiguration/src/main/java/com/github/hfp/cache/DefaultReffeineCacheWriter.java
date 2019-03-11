package com.github.hfp.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 在 DefaultRedisCacheWriter 基础上增加了 push 推送消息到指定 topic 的功能
 *
 */
public class DefaultReffeineCacheWriter implements ReffeineCacheWriter {
    private final Log LOGGER = LogFactory.getLog(getClass());
    private final RedisConnectionFactory connectionFactory;
    /**
     * sleepTime > 0 的时候，执行操作会加锁(SETNX)
     */
    private final Duration sleepTime;

    /**
     * @param connectionFactory must not be {@literal null}.
     */
    public DefaultReffeineCacheWriter(RedisConnectionFactory connectionFactory) {
        this(connectionFactory, Duration.ZERO);
    }

    /**
     * @param connectionFactory must not be {@literal null}.
     * @param sleepTime         sleep time between lock request attempts. Must not be {@literal null}. Use {@link Duration#ZERO}
     *                          to disable locking.
     */
    public DefaultReffeineCacheWriter(RedisConnectionFactory connectionFactory, Duration sleepTime) {

        Assert.notNull(connectionFactory, "ConnectionFactory must not be null!");
        Assert.notNull(sleepTime, "SleepTime must not be null!");

        this.connectionFactory = connectionFactory;
        this.sleepTime = sleepTime;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#put(java.lang.String, byte[], byte[], java.time.Duration)
     */
    @Override
    public void put(String name, byte[] key, byte[] value, @Nullable Duration ttl) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(value, "Value must not be null!");

        execute(name, connection -> {

            if (shouldExpireWithin(ttl)) {
                connection.set(key, value, Expiration.from(ttl.toMillis(), TimeUnit.MILLISECONDS),
                        RedisStringCommands.SetOption
                                .upsert());
            } else {
                connection.set(key, value);
            }

            return "OK";
        });
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#get(java.lang.String, byte[])
     */
    @Override
    public byte[] get(String name, byte[] key) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");

        return execute(name, connection -> connection.get(key));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#putIfAbsent(java.lang.String, byte[], byte[], java.time.Duration)
     */
    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, @Nullable Duration ttl) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(value, "Value must not be null!");

        return execute(name, connection -> {

            if (isLockingCacheWriter()) {
                doLock(name, connection);
            }

            try {
                if (connection.setNX(key, value)) {

                    if (shouldExpireWithin(ttl)) {
                        connection.pExpire(key, ttl.toMillis());
                    }
                    return null;
                }

                return connection.get(key);
            } finally {

                if (isLockingCacheWriter()) {
                    doUnlock(name, connection);
                }
            }
        });
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#remove(java.lang.String, byte[])
     */
    @Override
    public void remove(String name, byte[] key) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");

        execute(name, connection -> connection.del(key));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#clean(java.lang.String, byte[])
     */
    @Override
    public void clean(String name, byte[] pattern) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(pattern, "Pattern must not be null!");

        execute(name, connection -> {

                    boolean wasLocked = false;

                    try {

                        if (isLockingCacheWriter()) {
                            doLock(name, connection);
                            wasLocked = true;
                        }
                        Set<byte[]> keySet = new HashSet<>();
                        ScanOptions scanOptions = ScanOptions.scanOptions().match(new String(pattern)).count(500).build();
                        Cursor<byte[]> cursor = connection.scan(scanOptions);
                        while (cursor.hasNext()) {
                            byte[] key = cursor.next();
                            keySet.add(key);
                        }
                        byte[][] keys = keySet.toArray(new byte[0][]);
                        if (keys.length > 0) {
                            StringBuilder delKeys = new StringBuilder();
                            for (byte[] k : keys) {
                                delKeys.append(new String(k)).append(",");
                            }
                            LOGGER.warn("Start del cached key " + delKeys.toString());
                            connection.del(keys);
                        }
                    } finally {

                        if (wasLocked && isLockingCacheWriter()) {
                            doUnlock(name, connection);
                        }
                    }

                    return "OK";
                }

        );
    }

    @Override
    public void sync(byte[] channel, byte[] message) {
        LOGGER.info("start sync cache message ");
        Assert.notNull(channel, "Sync channel must not be null!");
        Assert.notNull(message, "Sync message must not be null!");

        executeLockFree(connection -> {
            final Long publish = connection.publish(channel, message);
            LOGGER.info("client recived sync message, subs count" + publish);
        });
    }

    private Boolean doLock(String name, RedisConnection connection) {
        return connection.setNX(createCacheLockKey(name), new byte[0]);
    }

    private Long doUnlock(String name, RedisConnection connection) {
        return connection.del(createCacheLockKey(name));
    }

    private boolean doCheckLock(String name, RedisConnection connection) {
        return connection.exists(createCacheLockKey(name));
    }

    /**
     * @return {@literal true} if {@link RedisCacheWriter} uses locks.
     */
    private boolean isLockingCacheWriter() {
        return !sleepTime.isZero() && !sleepTime.isNegative();
    }

    private <T> T execute(String name, Function<RedisConnection, T> callback) {

        RedisConnection connection = connectionFactory.getConnection();
        try {

            checkAndPotentiallyWaitUntilUnlocked(name, connection);
            return callback.apply(connection);
        } finally {
            connection.close();
        }
    }

    private void executeLockFree(Consumer<RedisConnection> callback) {

        RedisConnection connection = connectionFactory.getConnection();

        try {
            callback.accept(connection);
        } finally {
            connection.close();
        }
    }

    private void checkAndPotentiallyWaitUntilUnlocked(String name, RedisConnection connection) {

        if (!isLockingCacheWriter()) {
            return;
        }

        try {

            while (doCheckLock(name, connection)) {
                Thread.sleep(sleepTime.toMillis());
            }
        } catch (InterruptedException ex) {

            // Re-interrupt current thread, to allow other participants to react.
            Thread.currentThread().interrupt();

            throw new PessimisticLockingFailureException(
                    String.format("Interrupted while waiting to unlock cache %s", name),
                    ex);
        }
    }

    private static boolean shouldExpireWithin(@Nullable Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private static byte[] createCacheLockKey(String name) {
        return (name + "~lock").getBytes(StandardCharsets.UTF_8);
    }
}
