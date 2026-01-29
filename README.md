# Caffeine JCache Adapter

A high-performance JCache (JSR-107) implementation backed by [Caffeine](https://github.com/ben-manes/caffeine), providing a standards-compliant cache adapter with lazy expiration, event dispatching, statistics, and read/write-through support.

## Features

| Feature | Description |
|---------|-------------|
| **JCache API** | Full JSR-107 compliance with all core Cache operations |
| **Caffeine Backend** | High-performance ConcurrentHashMap-based storage |
| **Lazy Expiration** | No background threads - expiration checked on access |
| **By-Value Semantics** | Configurable deep copy via Copier abstraction |
| **Event Dispatching** | CREATED, UPDATED, REMOVED, EXPIRED event listeners |
| **Statistics** | Hits, misses, puts, removals, evictions tracking |
| **Read-Through** | CacheLoader integration for automatic loading |
| **Write-Through** | CacheWriter integration for write-behind |
| **EntryProcessor** | Atomic read-modify-write operations |
| **Async loadAll** | Asynchronous bulk loading with CompletionListener |

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.yms</groupId>
    <artifactId>yms-jcache</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

```java
import com.yms.cache.CaffeineCache;
import com.yms.cache.config.YmsConfiguration;
import javax.cache.expiry.EternalExpiryPolicy;

// Create configuration
YmsConfiguration<String, String> config = new YmsConfiguration<>();
config.setTypes(String.class, String.class);
config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
config.setStatisticsEnabled(true);

// Create cache
CaffeineCache<String, String> cache = new CaffeineCache<>("myCache", null, config);

// Basic CRUD
cache.put("key1", "value1");
String value = cache.get("key1");
boolean exists = cache.containsKey("key1");
cache.remove("key1");

// Batch operations
cache.putAll(Map.of("a", "1", "b", "2", "c", "3"));
Map<String, String> results = cache.getAll(Set.of("a", "b", "c"));
cache.removeAll(Set.of("a", "b"));

// Conditional operations
cache.putIfAbsent("key", "value");
cache.replace("key", "oldValue", "newValue");
String old = cache.getAndPut("key", "newValue");
String removed = cache.getAndRemove("key");

// Close when done
cache.close();
```

### Read-Through with CacheLoader

```java
CacheLoader<String, String> loader = key -> {
    // Load from database, API, etc.
    return database.get(key);
};

YmsConfiguration<String, String> config = new YmsConfiguration<>();
config.setTypes(String.class, String.class);
config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
config.setReadThrough(true);
config.setCacheLoaderFactory(() -> loader);

CaffeineCache<String, String> cache = new CaffeineCache<>("readThroughCache", null, config);

// Automatically loads from database on cache miss
String value = cache.get("user:123");
```

### Write-Through with CacheWriter

```java
CacheWriter<String, String> writer = new CacheWriter<>() {
    @Override
    public void write(Cache.Entry<? extends String, ? extends String> entry) {
        database.save(entry.getKey(), entry.getValue());
    }

    @Override
    public void delete(Object key) {
        database.delete(key);
    }

    // ... writeAll, deleteAll
};

YmsConfiguration<String, String> config = new YmsConfiguration<>();
config.setWriteThrough(true);
config.setCacheWriterFactory(() -> writer);
```

### EntryProcessor (Atomic Operations)

```java
// Atomic increment
Integer oldValue = cache.invoke("counter", (entry, args) -> {
    int current = entry.exists() ? entry.getValue() : 0;
    entry.setValue(current + 1);
    return current;
});

// Conditional update
cache.invoke("user:123", (entry, args) -> {
    if (entry.exists() && entry.getValue().isExpired()) {
        entry.remove();
    }
    return null;
});

// Process multiple entries
Map<String, EntryProcessorResult<Integer>> results =
    cache.invokeAll(Set.of("a", "b", "c"), (entry, args) -> {
        entry.setValue(entry.getValue() * 2);
        return entry.getValue();
    });
```

### Event Listeners

```java
CacheEntryCreatedListener<String, String> listener = events -> {
    for (CacheEntryEvent<? extends String, ? extends String> event : events) {
        System.out.println("Created: " + event.getKey() + "=" + event.getValue());
    }
};

MutableCacheEntryListenerConfiguration<String, String> listenerConfig =
    new MutableCacheEntryListenerConfiguration<>(
        () -> listener,  // listener factory
        null,            // filter factory (null = no filter)
        true,            // isOldValueRequired
        true             // isSynchronous
    );

cache.registerCacheEntryListener(listenerConfig);
```

### Expiry Policies

```java
import javax.cache.expiry.*;

// Eternal (never expires)
config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());

// Expire after creation
config.setExpiryPolicyFactory(
    CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 30))
);

// Expire after last access
config.setExpiryPolicyFactory(
    AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 10))
);

// Expire after last modification
config.setExpiryPolicyFactory(
    ModifiedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1))
);

// Touch on access (reset TTL)
config.setExpiryPolicyFactory(
    TouchedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 15))
);
```

### Statistics

```java
config.setStatisticsEnabled(true);

CaffeineCache<String, String> cache = new CaffeineCache<>("statsCache", null, config);

// ... perform operations ...

JCacheStatistics stats = cache.getStatistics();
System.out.println("Hits: " + stats.getCacheHits());
System.out.println("Misses: " + stats.getCacheMisses());
System.out.println("Hit Rate: " + stats.getCacheHitPercentage() + "%");
System.out.println("Puts: " + stats.getCachePuts());
System.out.println("Removals: " + stats.getCacheRemovals());
System.out.println("Evictions: " + stats.getCacheEvictions());
```

## API Reference

### Core Operations

| Method | Description |
|--------|-------------|
| `get(K key)` | Get value by key (triggers read-through if configured) |
| `getAll(Set<K> keys)` | Get multiple values |
| `put(K key, V value)` | Store key-value pair |
| `putAll(Map<K,V> map)` | Store multiple entries |
| `putIfAbsent(K key, V value)` | Store only if key doesn't exist |
| `remove(K key)` | Remove entry by key |
| `remove(K key, V oldValue)` | Remove only if value matches |
| `removeAll(Set<K> keys)` | Remove multiple entries |
| `removeAll()` | Remove all entries |
| `clear()` | Clear cache (no events fired) |
| `containsKey(K key)` | Check if key exists |

### Atomic Operations

| Method | Description |
|--------|-------------|
| `getAndPut(K key, V value)` | Put and return old value |
| `getAndRemove(K key)` | Remove and return old value |
| `getAndReplace(K key, V value)` | Replace and return old value |
| `replace(K key, V value)` | Replace if key exists |
| `replace(K key, V old, V new)` | Replace if value matches (CAS) |
| `invoke(K key, EntryProcessor)` | Atomic read-modify-write |
| `invokeAll(Set<K>, EntryProcessor)` | Process multiple entries |

### Bulk Loading

| Method | Description |
|--------|-------------|
| `loadAll(Set<K>, replace, listener)` | Async bulk load with CompletionListener |

### Lifecycle

| Method | Description |
|--------|-------------|
| `close()` | Close the cache |
| `isClosed()` | Check if cache is closed |
| `getName()` | Get cache name |

## Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `setTypes(keyType, valueType)` | Key and value types | Required |
| `setExpiryPolicyFactory(factory)` | Expiry policy | Required |
| `setStatisticsEnabled(boolean)` | Enable statistics | false |
| `setReadThrough(boolean)` | Enable read-through | false |
| `setCacheLoaderFactory(factory)` | CacheLoader factory | null |
| `setWriteThrough(boolean)` | Enable write-through | false |
| `setCacheWriterFactory(factory)` | CacheWriter factory | null |
| `setStoreByValue(boolean)` | Enable by-value semantics | true |
| `setKeyCopierFactory(factory)` | Key copier factory | SerializingCopier |
| `setValueCopierFactory(factory)` | Value copier factory | SerializingCopier |

## Performance

### Quick Results

Benchmark results with JMH on typical hardware:

| Operation | Throughput | Latency p50 | Latency p99 |
|-----------|------------|-------------|-------------|
| get (hit) | 13.3M ops/sec | 42 ns | 1.0 us |
| put (update) | ~5M ops/sec | ~100 ns | ~2 us |
| getAndPut | ~4M ops/sec | ~150 ns | ~3 us |

Key observations:
- Reads are 2-3x faster than writes
- Simple types outperform complex objects due to serialization overhead
- By-reference mode (`setStoreByValue(false)`) improves performance but requires immutable objects

### JMH Benchmarking

This project includes comprehensive JMH (Java Microbenchmark Harness) benchmarks for accurate performance measurement.

**Build benchmark JAR:**
```bash
mvn clean package -Pbenchmark -DskipTests
```

**List available benchmarks:**
```bash
java -jar target/benchmarks.jar -l
```

**Run all benchmarks:**
```bash
java -jar target/benchmarks.jar
```

**Run specific benchmark:**
```bash
# Read operations only
java -jar target/benchmarks.jar ".*ReadOperation.*"

# Concurrency tests
java -jar target/benchmarks.jar ".*Concurrency.*"

# With custom parameters
java -jar target/benchmarks.jar ".*ReadOperation.*" \
  -p cacheSize=100000 \
  -p storeByValue=true \
  -p statisticsEnabled=false
```

**Run with profilers:**
```bash
# GC profiler
java -jar target/benchmarks.jar -prof gc

# Stack profiler
java -jar target/benchmarks.jar -prof stack

# Output JSON results
java -jar target/benchmarks.jar -rf json -rff benchmark-results.json
```

### Benchmark Categories

| Category | Benchmarks | Description |
|----------|------------|-------------|
| **Read Operations** | get, getAll, containsKey | Cache hit/miss performance |
| **Write Operations** | put, putAll, putIfAbsent, remove | Write throughput |
| **Atomic Operations** | getAndPut, replace, invoke | Atomic read-modify-write |
| **Concurrency** | Mixed workload, hot key contention | Multi-threaded stress tests |
| **Integration** | Read-through, write-through | CacheLoader/Writer overhead |
| **Expiry** | CREATED, ACCESSED, MODIFIED | Expiration policy overhead |
| **Memory** | Large objects, serialization | GC pressure and memory usage |

### Parameterized Tests

| Parameter | Values | Description |
|-----------|--------|-------------|
| `cacheSize` | 10000, 100000 | Maximum cache entries |
| `dataSize` | 10000, 100000 | Test data size |
| `valueSize` | 100, 1024 | Value size in bytes |
| `storeByValue` | true, false | Serialization enabled |
| `statisticsEnabled` | true, false | Statistics overhead |
| `readRatio` | 0.8, 0.5, 0.2 | Read vs write ratio |
| `backendLatencyUs` | 0, 100, 1000 | Simulated backend latency |

Run simple example benchmarks:
```bash
mvn exec:java -Dexec.mainClass="com.yms.cache.example.ComplexDataExample"
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      CaffeineCache                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  JCache API Layer                    │   │
│  │  get/put/remove/replace/invoke/loadAll/...          │   │
│  └─────────────────────────────────────────────────────┘   │
│                            │                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Core Components                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐    │   │
│  │  │ Copier   │ │ Expiry   │ │ EventDispatcher  │    │   │
│  │  │(by-value)│ │Calculator│ │(CRUD events)     │    │   │
│  │  └──────────┘ └──────────┘ └──────────────────┘    │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐    │   │
│  │  │Statistics│ │CacheLoader│ │ CacheWriter      │    │   │
│  │  │(JMX)     │ │(read-thru)│ │ (write-thru)     │    │   │
│  │  └──────────┘ └──────────┘ └──────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
│                            │                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           Storage Layer (ConcurrentHashMap)          │   │
│  │      Map<K, Expirable<V>> with atomic operations     │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Principles

1. **Lazy Expiration**: No background threads - entries are checked/evicted on access
2. **Atomic Operations**: Single-key atomicity via `ConcurrentHashMap.compute()`
3. **Side Effect Separation**: Events and statistics recorded outside atomic blocks
4. **By-Value Semantics**: Deep copy via Copier abstraction for thread safety
5. **Reentry Detection**: EntryProcessor prevents recursive cache calls

## Project Structure

```
src/main/java/com/yms/cache/
├── CaffeineCache.java          # Main cache implementation
├── config/
│   └── YmsConfiguration.java   # Configuration builder
├── copier/
│   ├── Copier.java             # Copier interface
│   ├── IdentityCopier.java     # By-reference (no copy)
│   └── SerializingCopier.java  # Serialization-based copy
├── event/
│   └── CacheEventDispatcher.java
├── expiry/
│   └── ExpiryCalculator.java
├── internal/
│   ├── CacheEntryAdapter.java  # EntryProcessor support
│   └── Expirable.java          # Value wrapper with TTL
├── stats/
│   └── JCacheStatistics.java
└── example/
    ├── CaffeineCacheExample.java    # Basic usage examples
    └── ComplexDataExample.java      # Complex data & benchmarks

src/test/java/com/yms/cache/
├── CaffeineCache*Test.java     # Unit tests (18 test classes)
└── benchmark/                  # JMH performance benchmarks
    ├── data/
    │   ├── BenchmarkData.java      # Test data generators
    │   └── ComplexObject.java      # Complex serializable objects
    ├── state/
    │   ├── CacheState.java         # Cache setup state
    │   └── DataState.java          # Test data state
    └── scenarios/
        ├── ReadOperationBenchmarks.java   # get, getAll, containsKey
        ├── WriteOperationBenchmarks.java  # put, putAll, remove
        ├── AtomicOperationBenchmarks.java # getAndPut, replace, invoke
        ├── ConcurrencyBenchmarks.java     # Multi-threaded stress tests
        ├── IntegrationBenchmarks.java     # Read/write-through
        ├── ExpiryBenchmarks.java          # Expiration policies
        └── MemoryBenchmarks.java          # GC pressure tests
```

## Build & Test

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Run examples
mvn exec:java -Dexec.mainClass="com.yms.cache.example.CaffeineCacheExample"
mvn exec:java -Dexec.mainClass="com.yms.cache.example.ComplexDataExample"

# Build JMH benchmark JAR
mvn clean package -Pbenchmark -DskipTests

# Run benchmarks
java -jar target/benchmarks.jar

# Quick smoke test (minimal iterations)
java -jar target/benchmarks.jar ".*ReadOperation.*benchmarkGetSequential" \
  -wi 1 -i 1 -f 1 \
  -p cacheSize=10000 -p storeByValue=false -p statisticsEnabled=false \
  -p dataSize=10000 -p valueSize=100
```

## Test Coverage

- 308 tests
- ~81% line coverage
- Covers all JCache operations, edge cases, and error handling

## Design Notes

This project follows the design constraints documented in `dev_plan/JSR-107详细设计方案.md`:
- Single-key atomicity with Caffeine `asMap()` operations
- Strict separation of side effects from atomic state transitions
- Two-phase read-through for EntryProcessor to avoid nested CacheLoader calls
- ThreadLocal-based reentry detection for EntryProcessor safety

## License

MIT License
