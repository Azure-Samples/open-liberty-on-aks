package cafe.model;

import cafe.model.entity.Coffee;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.jcache.configuration.RedissonConfiguration;

@ApplicationScoped
public class CafeRepository {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
    private static final String COFFEES_CACHE_NAME = "jakartaee-cafe-coffees";
    private static final String COUNTER_CACHE_NAME = "jakartaee-cafe-counter";
    private static final String COUNTER_CACHE_KEY = "counter";

    private AtomicLong counter;
    private Cache<Long, Coffee> cache;
    private Cache<String, Long> counterCache;

    @Inject
    @ConfigProperty(name = "REDIS_CACHE_ADDRESS")
    private String redisCacheAddress;

    @Inject
    @ConfigProperty(name = "REDIS_CACHE_KEY")
    private Optional<String> redisCacheKey;

    @PostConstruct
    private void init() {
        Config redissonconfig = new Config();
        if (redisCacheAddress == null || redisCacheAddress.isEmpty()) {
            logger.log(Level.WARNING, "REDIS_CACHE_ADDRESS environment variable is not set or empty.");
            throw new IllegalStateException("REDIS_CACHE_ADDRESS environment variable is required.");
        }

        if (redisCacheKey.isEmpty()) {
            logger.log(Level.INFO, "REDIS_CACHE_KEY is not set or empty. Connecting to Redis without password.");
            redissonconfig.useSingleServer().setAddress(redisCacheAddress);
        } else {
            redissonconfig.useSingleServer().setAddress(redisCacheAddress).setPassword(redisCacheKey.get());
        }

        RedissonClient redissonClient = Redisson.create(redissonconfig);
        CacheManager manager = Caching.getCachingProvider().getCacheManager();

        MutableConfiguration<Long, Coffee> jcacheConfig = new MutableConfiguration<>();
        Configuration<Long, Coffee> config = RedissonConfiguration.fromInstance(redissonClient, jcacheConfig);
        cache = manager.getCache(COFFEES_CACHE_NAME);
        if (cache == null) {
            cache = manager.createCache(COFFEES_CACHE_NAME, config);
        }

        MutableConfiguration<String, Long> counterCacheConfig = new MutableConfiguration<>();
        Configuration<String, Long> counterConfig = RedissonConfiguration.fromInstance(redissonClient, counterCacheConfig);
        counterCache = manager.getCache(COUNTER_CACHE_NAME);
        if (counterCache == null) {
            counterCache = manager.createCache(COUNTER_CACHE_NAME, counterConfig);
        }
        if (counterCache.containsKey(COUNTER_CACHE_KEY)) {
            counter = new AtomicLong(counterCache.get(COUNTER_CACHE_KEY));
        } else {
            counter = new AtomicLong(0);
            counterCache.put(COUNTER_CACHE_KEY, counter.get());
        }
    }
    
    public List<Coffee> getAllCoffees() {
        logger.log(Level.INFO, "Finding all coffees.");
        
        List<Coffee> coffeeList = new ArrayList<>();
        cache.forEach(entry -> coffeeList.add(entry.getValue()));
        
        return coffeeList;
    }

    public Coffee persistCoffee(Coffee coffee) {
        coffee.setId(counter.incrementAndGet());
        logger.log(Level.INFO, "Persisting the new coffee {0}.", coffee);
        
        cache.put(coffee.getId(), coffee);
        counterCache.put("counter", counter.get());
        return coffee;
    }

    public void removeCoffeeById(Long coffeeId) {
        logger.log(Level.INFO, "Removing a coffee {0}.", coffeeId);
        
        cache.remove(coffeeId);
    }

    public Coffee findCoffeeById(Long coffeeId) {
        logger.log(Level.INFO, "Finding the coffee with id {0}.", coffeeId);
        
        return cache.get(coffeeId);
    }
}
