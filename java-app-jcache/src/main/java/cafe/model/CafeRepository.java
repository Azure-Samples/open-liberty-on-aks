package cafe.model;

import cafe.model.entity.Coffee;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.jcache.configuration.RedissonConfiguration;

@ApplicationScoped
public class CafeRepository {

	private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
	private AtomicLong counter;
	private Cache<String, Coffee> cache;
	private Cache<String, Long> counterCache;
	
	@PostConstruct
	private void init() {
		Config redissonconfig = new Config();
		redissonconfig.useSingleServer().setPassword(System.getenv("REDIS_CACHE_KEY"))
			.setAddress(System.getenv("REDIS_CACHE_ADDRESS"));

		RedissonClient redissonClient = Redisson.create(redissonconfig);
		CacheManager manager = Caching.getCachingProvider().getCacheManager();

		MutableConfiguration<String, Coffee> jcacheConfig = new MutableConfiguration<>();
		Configuration<String, Coffee> config = RedissonConfiguration.fromInstance(redissonClient, jcacheConfig);
		cache = manager.getCache("jakarta-ee-cafe-coffees");
		if (cache == null) {
			cache = manager.createCache("jakarta-ee-cafe-coffees", config);
		}

		MutableConfiguration<String, Long> counterCacheConfig = new MutableConfiguration<>();
		Configuration<String, Long> counterConfig = RedissonConfiguration.fromInstance(redissonClient, counterCacheConfig);
		counterCache = manager.getCache("jakarta-ee-cafe-counter");
		if (counterCache == null) {
			counterCache = manager.createCache("jakarta-ee-cafe-counter", counterConfig);
		}
		if (counterCache.containsKey("counter")) {
			counter = new AtomicLong(counterCache.get("counter"));
		} else {
			counter = new AtomicLong(0);
			counterCache.put("counter", counter.get());
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
		
		cache.put(coffee.getId().toString(), coffee);
		counterCache.put("counter", counter.get());
		return coffee;
	}

	public void removeCoffeeById(Long coffeeId) {
		logger.log(Level.INFO, "Removing a coffee {0}.", coffeeId);
		
		cache.remove(coffeeId.toString());
	}

	public Coffee findCoffeeById(Long coffeeId) {
		logger.log(Level.INFO, "Finding the coffee with id {0}.", coffeeId);
		
		return cache.get(coffeeId.toString());
	}
}
