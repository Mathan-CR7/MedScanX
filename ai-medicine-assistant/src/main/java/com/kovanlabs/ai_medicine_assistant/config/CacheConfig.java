package com.kovanlabs.ai_medicine_assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    @Primary
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        log.info("Configuring Redis & In-Memory Fallback CacheManager...");
        try {
            // Test connection to see if Redis is running
            connectionFactory.getConnection().close();

            ObjectMapper redisObjectMapper = objectMapper.copy();
            redisObjectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            redisObjectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            redisObjectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            GenericJackson2JsonRedisSerializer jsonSerializer = GenericJackson2JsonRedisSerializer.builder()
                    .objectMapper(redisObjectMapper)
                    .defaultTyping(true)
                    .build();

            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(24))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                    .disableCachingNullValues();

            log.info("Successfully connected to Redis. Configuring RedisCacheManager with type-safe JSON serialization.");
            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(config)
                    .build();
        } catch (Exception e) {
            log.warn("Redis is not available (failed to connect: {}). Falling back to local in-memory ConcurrentMapCacheManager.", e.getMessage());
            return new ConcurrentMapCacheManager("medicinePrices", "medicineSideEffects", "medicineAlternatives", "medicineFoodSupport", "fullMedicineReports");
        }
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[Cache GET Resilience] Failed to read key '{}' from cache '{}' ({}). Evicting key and serving fresh request.",
                        key, cache.getName(), exception.getMessage());
                try {
                    cache.evict(key);
                } catch (Exception e) {
                    log.debug("Failed to evict key '{}' after cache GET error: {}", key, e.getMessage());
                }
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("[Cache PUT Resilience] Failed to put key '{}' into cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[Cache EVICT Resilience] Failed to evict key '{}' from cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("[Cache CLEAR Resilience] Failed to clear cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
