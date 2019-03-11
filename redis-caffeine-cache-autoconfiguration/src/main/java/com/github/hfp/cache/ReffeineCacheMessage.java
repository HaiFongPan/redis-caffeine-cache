package com.github.hfp.cache;

import java.io.Serializable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * 通知多机清除缓存消息
 *
 */
public class ReffeineCacheMessage implements Serializable {

    private static final long serialVersionUID = 2275439732256223065L;
    /**
     * 缓存名字
     */
    @NonNull
    private String name;
    /**
     * 缓存 Key
     */
    @Nullable
    private Object key;
    /**
     * 发起缓存同步的机器
     */
    @NonNull
    private String source;

    public ReffeineCacheMessage(String name, @Nullable Object key, String source) {
        this.name = name;
        this.key = key;
        this.source = source;
    }

    public ReffeineCacheMessage() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getKey() {
        return key;
    }

    public void setKey(Object key) {
        this.key = key;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
