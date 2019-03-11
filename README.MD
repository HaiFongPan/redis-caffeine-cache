### 简介

简单的`SpringCache`二级缓存, redis + caffeine


### 应用

```xml
    上传到私有 mvn 库后导入依赖
    <dependency>
        <groupId>com.github.hfp</groupId>
        <artifactId>redis-caffeine-cache-starter</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>

```

### 配置

```properties
# application.properties
# 通知同步缓存的redis topic, 默认redis:caffeine:sync:channel
reffeine.cache.channel=redis:caffeine:sync:channel:rc
# caffeine 缓存的配置
reffeine.cache.caffeine-spec=expireAfterWrite=7d,maximumSize=5000
# 缓存前缀
reffeine.cache.prefix=RC
# redis 缓存过期时间, 可空,默认永久
reffeine.cache.redis-cache-ttl=
# 允许null? 默认true
reffeine.cache.allow-null-values=true
# 默认初始化缓存, 可空
reffeine.cache.initial-caches=aaaa,bbbb,ccc
```

### TODO

支持 pattern 配置过滤多机IP