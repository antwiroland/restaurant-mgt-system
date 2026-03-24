package com.restaurantmanager.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProps.class)
public class CacheConfig {
    public static final String MENU_CATEGORIES = "menuCategories";
    public static final String MENU_ITEMS_PUBLIC = "menuItemsPublic";
    public static final String MENU_ITEM_PUBLIC_BY_ID = "menuItemPublicById";
    public static final String TABLES = "tables";
    public static final String TABLE_QR = "tableQr";
    public static final String TABLE_SCAN = "tableScan";

    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = "redis-enabled", havingValue = "true")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory, CacheProps cacheProps) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(cacheProps.getDefaultTtlSeconds()))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()
                ));

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(MENU_CATEGORIES, base.entryTtl(Duration.ofMinutes(10)));
        perCache.put(MENU_ITEMS_PUBLIC, base.entryTtl(Duration.ofMinutes(5)));
        perCache.put(MENU_ITEM_PUBLIC_BY_ID, base.entryTtl(Duration.ofMinutes(5)));
        perCache.put(TABLES, base.entryTtl(Duration.ofMinutes(2)));
        perCache.put(TABLE_QR, base.entryTtl(Duration.ofMinutes(10)));
        perCache.put(TABLE_SCAN, base.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager inMemoryCacheManager() {
        return new ConcurrentMapCacheManager(
                MENU_CATEGORIES,
                MENU_ITEMS_PUBLIC,
                MENU_ITEM_PUBLIC_BY_ID,
                TABLES,
                TABLE_QR,
                TABLE_SCAN
        );
    }
}
