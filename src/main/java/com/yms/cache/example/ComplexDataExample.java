package com.yms.cache.example;

import com.yms.cache.CaffeineCache;
import com.yms.cache.config.YmsConfiguration;

import javax.cache.expiry.EternalExpiryPolicy;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Advanced example demonstrating CaffeineCache with complex data structures
 * and performance benchmarks.
 *
 * Features demonstrated:
 * - Complex nested objects (User, Order, Product hierarchies)
 * - JSON-like Map structures with mixed types
 * - Long text content (articles, documents)
 * - Performance benchmarks with 10,000 records
 * - Batch operation performance comparison
 */
public class ComplexDataExample {

    private static final int BENCHMARK_SIZE = 10_000;
    private static final Random random = new Random(42); // Fixed seed for reproducibility

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("CaffeineCache Complex Data & Performance Example");
        System.out.println("=".repeat(70));

        // Complex data structure examples
        example1_NestedObjects();
        example2_JsonLikeMaps();
        example3_LongTextContent();
        example4_MixedTypeCollections();

        // Performance benchmarks
        benchmark1_SimpleStringPerformance();
        benchmark2_ComplexObjectPerformance();
        benchmark3_LargeTextPerformance();
        benchmark4_BatchVsIndividualOperations();
        benchmark5_ReadVsWritePerformance();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("All examples and benchmarks completed!");
        System.out.println("=".repeat(70));
    }

    // ========== Complex Data Classes ==========

    /**
     * Complex User object with nested Address and multiple collections.
     */
    static class User implements Serializable {
        private static final long serialVersionUID = 1L;

        String id;
        String username;
        String email;
        Address address;
        List<String> roles;
        Map<String, Object> preferences;
        List<Order> orderHistory;
        LocalDateTime createdAt;
        LocalDateTime lastLogin;

        User(String id, String username, String email) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.roles = new ArrayList<>();
            this.preferences = new HashMap<>();
            this.orderHistory = new ArrayList<>();
            this.createdAt = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return String.format("User{id='%s', username='%s', email='%s', roles=%s, orders=%d}",
                id, username, email, roles, orderHistory.size());
        }
    }

    /**
     * Nested Address object.
     */
    static class Address implements Serializable {
        private static final long serialVersionUID = 1L;

        String street;
        String city;
        String state;
        String zipCode;
        String country;
        Map<String, String> additionalInfo;

        Address(String street, String city, String state, String zipCode, String country) {
            this.street = street;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
            this.country = country;
            this.additionalInfo = new HashMap<>();
        }

        @Override
        public String toString() {
            return String.format("%s, %s, %s %s, %s", street, city, state, zipCode, country);
        }
    }

    /**
     * Order with nested Product list and payment info.
     */
    static class Order implements Serializable {
        private static final long serialVersionUID = 1L;

        String orderId;
        String userId;
        List<OrderItem> items;
        double totalAmount;
        String status;
        PaymentInfo payment;
        LocalDateTime orderDate;
        Map<String, Object> metadata;

        Order(String orderId, String userId) {
            this.orderId = orderId;
            this.userId = userId;
            this.items = new ArrayList<>();
            this.metadata = new HashMap<>();
            this.orderDate = LocalDateTime.now();
            this.status = "PENDING";
        }

        void calculateTotal() {
            this.totalAmount = items.stream()
                .mapToDouble(item -> item.price * item.quantity)
                .sum();
        }

        @Override
        public String toString() {
            return String.format("Order{id='%s', items=%d, total=%.2f, status='%s'}",
                orderId, items.size(), totalAmount, status);
        }
    }

    /**
     * Order item with product reference.
     */
    static class OrderItem implements Serializable {
        private static final long serialVersionUID = 1L;

        String productId;
        String productName;
        int quantity;
        double price;
        Map<String, String> attributes;

        OrderItem(String productId, String productName, int quantity, double price) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.price = price;
            this.attributes = new HashMap<>();
        }
    }

    /**
     * Payment information.
     */
    static class PaymentInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        String method; // CREDIT_CARD, PAYPAL, BANK_TRANSFER
        String transactionId;
        double amount;
        String currency;
        Map<String, String> details;

        PaymentInfo(String method, double amount, String currency) {
            this.method = method;
            this.amount = amount;
            this.currency = currency;
            this.transactionId = UUID.randomUUID().toString();
            this.details = new HashMap<>();
        }
    }

    /**
     * Article with long text content.
     */
    static class Article implements Serializable {
        private static final long serialVersionUID = 1L;

        String id;
        String title;
        String content; // Long text
        String author;
        List<String> tags;
        Map<String, Object> metadata;
        List<Comment> comments;
        LocalDateTime publishedAt;

        Article(String id, String title, String content, String author) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.author = author;
            this.tags = new ArrayList<>();
            this.metadata = new HashMap<>();
            this.comments = new ArrayList<>();
            this.publishedAt = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return String.format("Article{id='%s', title='%s', contentLength=%d, comments=%d}",
                id, title, content.length(), comments.size());
        }
    }

    /**
     * Comment on an article.
     */
    static class Comment implements Serializable {
        private static final long serialVersionUID = 1L;

        String id;
        String userId;
        String content;
        int likes;
        List<Comment> replies;
        LocalDateTime createdAt;

        Comment(String id, String userId, String content) {
            this.id = id;
            this.userId = userId;
            this.content = content;
            this.likes = 0;
            this.replies = new ArrayList<>();
            this.createdAt = LocalDateTime.now();
        }
    }

    // ========== Example 1: Nested Objects ==========

    private static void example1_NestedObjects() {
        printHeader("Example 1: Complex Nested Objects (User -> Address -> Orders -> Items)");

        CaffeineCache<String, User> cache = createCache("nestedObjectCache", User.class);

        // Create complex user with nested data
        User user = createComplexUser("user-001", "john_doe", "john@example.com");

        // Cache the user
        cache.put(user.id, user);
        System.out.println("Cached user: " + user);
        System.out.println("  Address: " + user.address);
        System.out.println("  Roles: " + user.roles);
        System.out.println("  Preferences: " + user.preferences);
        System.out.println("  Order history: " + user.orderHistory.size() + " orders");

        // Retrieve and verify
        User retrieved = cache.get(user.id);
        System.out.println("\nRetrieved user: " + retrieved);
        System.out.println("  First order: " + retrieved.orderHistory.get(0));
        System.out.println("  Order items: " + retrieved.orderHistory.get(0).items.size());

        // EntryProcessor to update nested data
        cache.invoke(user.id, (entry, args) -> {
            User u = entry.getValue();
            u.lastLogin = LocalDateTime.now();
            u.preferences.put("lastVisitedPage", "/dashboard");
            entry.setValue(u);
            return null;
        });

        User updated = cache.get(user.id);
        System.out.println("\nAfter update via EntryProcessor:");
        System.out.println("  Last login: " + updated.lastLogin);
        System.out.println("  Preferences: " + updated.preferences);

        cache.close();
    }

    // ========== Example 2: JSON-like Maps ==========

    private static void example2_JsonLikeMaps() {
        printHeader("Example 2: JSON-like Map Structures with Mixed Types");

        CaffeineCache<String, Map<String, Object>> cache = createMapCache("jsonCache");

        // Create JSON-like nested structure
        Map<String, Object> apiResponse = new LinkedHashMap<>();
        apiResponse.put("status", 200);
        apiResponse.put("success", true);
        apiResponse.put("timestamp", System.currentTimeMillis());

        // Nested data object
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalRecords", 1523);
        data.put("page", 1);
        data.put("pageSize", 50);

        // Array of results
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "item-" + i);
            item.put("name", "Product " + i);
            item.put("price", 99.99 + i * 10);
            item.put("inStock", i % 2 == 0);
            item.put("tags", Arrays.asList("tag1", "tag2", "tag" + i));

            // Nested specifications
            Map<String, Object> specs = new LinkedHashMap<>();
            specs.put("weight", "1.5kg");
            specs.put("dimensions", Map.of("width", 10, "height", 20, "depth", 5));
            specs.put("colors", Arrays.asList("red", "blue", "green"));
            item.put("specifications", specs);

            results.add(item);
        }
        data.put("results", results);
        apiResponse.put("data", data);

        // Metadata
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", UUID.randomUUID().toString());
        meta.put("processingTimeMs", 45);
        meta.put("cacheHit", false);
        apiResponse.put("meta", meta);

        // Cache the JSON-like structure
        cache.put("api-response-1", apiResponse);
        System.out.println("Cached JSON-like API response:");
        printJsonLike(apiResponse, 0);

        // Retrieve and access nested data
        Map<String, Object> retrieved = cache.get("api-response-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedData = (Map<String, Object>) retrieved.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> retrievedResults = (List<Map<String, Object>>) retrievedData.get("results");

        System.out.println("\nAccessing nested data:");
        System.out.println("  Status: " + retrieved.get("status"));
        System.out.println("  Total records: " + retrievedData.get("totalRecords"));
        System.out.println("  First result name: " + retrievedResults.get(0).get("name"));

        cache.close();
    }

    // ========== Example 3: Long Text Content ==========

    private static void example3_LongTextContent() {
        printHeader("Example 3: Long Text Content (Articles with 10KB+ content)");

        CaffeineCache<String, Article> cache = createCache("articleCache", Article.class);

        // Create articles with varying content lengths
        int[] contentSizes = {1_000, 5_000, 10_000, 50_000, 100_000};

        for (int size : contentSizes) {
            String content = generateLongText(size);
            Article article = new Article(
                "article-" + size,
                "Article with " + size + " characters",
                content,
                "Author " + (size / 1000)
            );
            article.tags.addAll(Arrays.asList("tech", "tutorial", "java"));
            article.metadata.put("wordCount", content.split("\\s+").length);
            article.metadata.put("readingTimeMinutes", content.length() / 1500);

            // Add some comments
            for (int i = 0; i < 5; i++) {
                Comment comment = new Comment(
                    "comment-" + i,
                    "user-" + i,
                    generateLongText(100 + random.nextInt(200))
                );
                comment.likes = random.nextInt(100);
                article.comments.add(comment);
            }

            cache.put(article.id, article);
            System.out.printf("Cached article: %s (content: %,d chars, comments: %d)%n",
                article.id, size, article.comments.size());
        }

        // Retrieve and verify
        Article large = cache.get("article-100000");
        System.out.println("\nRetrieved large article:");
        System.out.println("  Title: " + large.title);
        System.out.println("  Content length: " + large.content.length() + " chars");
        System.out.println("  Word count: " + large.metadata.get("wordCount"));
        System.out.println("  Reading time: " + large.metadata.get("readingTimeMinutes") + " minutes");
        System.out.println("  First 100 chars: " + large.content.substring(0, 100) + "...");

        cache.close();
    }

    // ========== Example 4: Mixed Type Collections ==========

    private static void example4_MixedTypeCollections() {
        printHeader("Example 4: Mixed Type Collections");

        CaffeineCache<String, List<Object>> cache = createListCache("mixedCache");

        // Create a list with various types
        List<Object> mixedData = new ArrayList<>();
        mixedData.add("String value");
        mixedData.add(12345);
        mixedData.add(3.14159);
        mixedData.add(true);
        mixedData.add(null);
        mixedData.add(Arrays.asList(1, 2, 3, 4, 5));
        mixedData.add(Map.of("key1", "value1", "key2", 123));
        mixedData.add(new int[]{10, 20, 30});
        mixedData.add(LocalDateTime.now());

        // Nested complex object
        User nestedUser = createComplexUser("nested-user", "nested", "nested@test.com");
        mixedData.add(nestedUser);

        cache.put("mixed-1", mixedData);
        System.out.println("Cached mixed type list with " + mixedData.size() + " elements:");
        for (int i = 0; i < mixedData.size(); i++) {
            Object item = mixedData.get(i);
            String type = item == null ? "null" : item.getClass().getSimpleName();
            String value = item == null ? "null" :
                (item instanceof int[] ? Arrays.toString((int[]) item) : String.valueOf(item));
            if (value.length() > 60) {
                value = value.substring(0, 60) + "...";
            }
            System.out.printf("  [%d] (%s) %s%n", i, type, value);
        }

        cache.close();
    }

    // ========== Benchmark 1: Simple String Performance ==========

    private static void benchmark1_SimpleStringPerformance() {
        printHeader("Benchmark 1: Simple String Performance (" + BENCHMARK_SIZE + " records)");

        CaffeineCache<String, String> cache = createCache("stringBenchmark", String.class);

        // Prepare data
        Map<String, String> testData = new LinkedHashMap<>();
        for (int i = 0; i < BENCHMARK_SIZE; i++) {
            testData.put("key-" + i, "value-" + i + "-" + UUID.randomUUID());
        }

        // Benchmark WRITE
        long startWrite = System.nanoTime();
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
        long writeTime = System.nanoTime() - startWrite;

        // Benchmark READ (sequential)
        long startReadSeq = System.nanoTime();
        for (String key : testData.keySet()) {
            cache.get(key);
        }
        long readSeqTime = System.nanoTime() - startReadSeq;

        // Benchmark READ (random access)
        List<String> keys = new ArrayList<>(testData.keySet());
        Collections.shuffle(keys);
        long startReadRandom = System.nanoTime();
        for (String key : keys) {
            cache.get(key);
        }
        long readRandomTime = System.nanoTime() - startReadRandom;

        printBenchmarkResults("Simple String", BENCHMARK_SIZE, writeTime, readSeqTime, readRandomTime);
        cache.close();
    }

    // ========== Benchmark 2: Complex Object Performance ==========

    private static void benchmark2_ComplexObjectPerformance() {
        printHeader("Benchmark 2: Complex Object Performance (" + BENCHMARK_SIZE + " records)");

        CaffeineCache<String, User> cache = createCache("objectBenchmark", User.class);

        // Prepare data
        Map<String, User> testData = new LinkedHashMap<>();
        for (int i = 0; i < BENCHMARK_SIZE; i++) {
            User user = createComplexUser(
                "user-" + i,
                "username_" + i,
                "user" + i + "@example.com"
            );
            testData.put(user.id, user);
        }

        // Benchmark WRITE
        long startWrite = System.nanoTime();
        for (Map.Entry<String, User> entry : testData.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
        long writeTime = System.nanoTime() - startWrite;

        // Benchmark READ (sequential)
        long startReadSeq = System.nanoTime();
        for (String key : testData.keySet()) {
            cache.get(key);
        }
        long readSeqTime = System.nanoTime() - startReadSeq;

        // Benchmark READ (random)
        List<String> keys = new ArrayList<>(testData.keySet());
        Collections.shuffle(keys);
        long startReadRandom = System.nanoTime();
        for (String key : keys) {
            cache.get(key);
        }
        long readRandomTime = System.nanoTime() - startReadRandom;

        printBenchmarkResults("Complex Object", BENCHMARK_SIZE, writeTime, readSeqTime, readRandomTime);
        cache.close();
    }

    // ========== Benchmark 3: Large Text Performance ==========

    private static void benchmark3_LargeTextPerformance() {
        printHeader("Benchmark 3: Large Text Performance (1KB per record, " + BENCHMARK_SIZE + " records)");

        CaffeineCache<String, String> cache = createCache("textBenchmark", String.class);

        // Prepare data (1KB text per entry)
        Map<String, String> testData = new LinkedHashMap<>();
        for (int i = 0; i < BENCHMARK_SIZE; i++) {
            testData.put("doc-" + i, generateLongText(1024));
        }

        // Benchmark WRITE
        long startWrite = System.nanoTime();
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
        long writeTime = System.nanoTime() - startWrite;

        // Benchmark READ
        long startRead = System.nanoTime();
        for (String key : testData.keySet()) {
            cache.get(key);
        }
        long readTime = System.nanoTime() - startRead;

        System.out.printf("Results (1KB text x %,d records = ~%,d KB total):%n",
            BENCHMARK_SIZE, BENCHMARK_SIZE);
        System.out.printf("  Write: %,d ops in %.2f ms (%.2f ops/sec)%n",
            BENCHMARK_SIZE, writeTime / 1_000_000.0, BENCHMARK_SIZE * 1_000_000_000.0 / writeTime);
        System.out.printf("  Read:  %,d ops in %.2f ms (%.2f ops/sec)%n",
            BENCHMARK_SIZE, readTime / 1_000_000.0, BENCHMARK_SIZE * 1_000_000_000.0 / readTime);

        cache.close();
    }

    // ========== Benchmark 4: Batch vs Individual Operations ==========

    private static void benchmark4_BatchVsIndividualOperations() {
        printHeader("Benchmark 4: Batch vs Individual Operations (" + BENCHMARK_SIZE + " records)");

        // Prepare data
        Map<String, String> testData = new LinkedHashMap<>();
        for (int i = 0; i < BENCHMARK_SIZE; i++) {
            testData.put("batch-key-" + i, "batch-value-" + i);
        }
        Set<String> keys = testData.keySet();

        // Test 1: Individual PUT
        CaffeineCache<String, String> cache1 = createCache("individualPut", String.class);
        long startIndividualPut = System.nanoTime();
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            cache1.put(entry.getKey(), entry.getValue());
        }
        long individualPutTime = System.nanoTime() - startIndividualPut;

        // Test 2: Batch PUT
        CaffeineCache<String, String> cache2 = createCache("batchPut", String.class);
        long startBatchPut = System.nanoTime();
        cache2.putAll(testData);
        long batchPutTime = System.nanoTime() - startBatchPut;

        // Test 3: Individual GET
        long startIndividualGet = System.nanoTime();
        for (String key : keys) {
            cache1.get(key);
        }
        long individualGetTime = System.nanoTime() - startIndividualGet;

        // Test 4: Batch GET
        long startBatchGet = System.nanoTime();
        cache2.getAll(keys);
        long batchGetTime = System.nanoTime() - startBatchGet;

        System.out.printf("PUT Operations (%,d records):%n", BENCHMARK_SIZE);
        System.out.printf("  Individual: %.2f ms (%.2f ops/sec)%n",
            individualPutTime / 1_000_000.0, BENCHMARK_SIZE * 1_000_000_000.0 / individualPutTime);
        System.out.printf("  Batch:      %.2f ms (%.2f ops/sec)%n",
            batchPutTime / 1_000_000.0, BENCHMARK_SIZE * 1_000_000_000.0 / batchPutTime);
        System.out.printf("  Speedup:    %.2fx%n", (double) individualPutTime / batchPutTime);

        System.out.printf("%nGET Operations (%,d records):%n", BENCHMARK_SIZE);
        System.out.printf("  Individual: %.2f ms (%.2f ops/sec)%n",
            individualGetTime / 1_000_000.0, BENCHMARK_SIZE * 1_000_000_000.0 / individualGetTime);
        System.out.printf("  Batch:      %.2f ms (%.2f ops/sec)%n",
            batchGetTime / 1_000_000.0, BENCHMARK_SIZE * 1_000_000_000.0 / batchGetTime);
        System.out.printf("  Speedup:    %.2fx%n", (double) individualGetTime / batchGetTime);

        cache1.close();
        cache2.close();
    }

    // ========== Benchmark 5: Read vs Write Performance ==========

    private static void benchmark5_ReadVsWritePerformance() {
        printHeader("Benchmark 5: Read vs Write Performance Comparison");

        CaffeineCache<String, String> cache = createCache("readWriteBenchmark", String.class);

        // Pre-populate cache
        Map<String, String> testData = new LinkedHashMap<>();
        for (int i = 0; i < BENCHMARK_SIZE; i++) {
            String key = "rw-key-" + i;
            String value = "rw-value-" + i + "-" + generateLongText(100);
            testData.put(key, value);
            cache.put(key, value);
        }

        List<String> keys = new ArrayList<>(testData.keySet());

        // Multiple read/write iterations
        int iterations = 5;
        long totalWriteTime = 0;
        long totalReadTime = 0;

        System.out.printf("Running %d iterations of %,d operations each:%n", iterations, BENCHMARK_SIZE);

        for (int iter = 1; iter <= iterations; iter++) {
            Collections.shuffle(keys);

            // Write (update existing entries)
            long writeStart = System.nanoTime();
            for (String key : keys) {
                cache.put(key, "updated-" + key + "-" + iter);
            }
            long writeTime = System.nanoTime() - writeStart;
            totalWriteTime += writeTime;

            // Read
            Collections.shuffle(keys);
            long readStart = System.nanoTime();
            for (String key : keys) {
                cache.get(key);
            }
            long readTime = System.nanoTime() - readStart;
            totalReadTime += readTime;

            System.out.printf("  Iteration %d: Write=%.2fms, Read=%.2fms%n",
                iter, writeTime / 1_000_000.0, readTime / 1_000_000.0);
        }

        double avgWriteMs = totalWriteTime / iterations / 1_000_000.0;
        double avgReadMs = totalReadTime / iterations / 1_000_000.0;
        double writeOpsPerSec = BENCHMARK_SIZE * 1_000_000_000.0 / (totalWriteTime / iterations);
        double readOpsPerSec = BENCHMARK_SIZE * 1_000_000_000.0 / (totalReadTime / iterations);

        System.out.printf("%nAverage Performance (%,d ops per iteration):%n", BENCHMARK_SIZE);
        System.out.printf("  Write: %.2f ms (%.2f ops/sec)%n", avgWriteMs, writeOpsPerSec);
        System.out.printf("  Read:  %.2f ms (%.2f ops/sec)%n", avgReadMs, readOpsPerSec);
        System.out.printf("  Read/Write ratio: %.2fx faster reads%n", writeOpsPerSec / readOpsPerSec);

        cache.close();
    }

    // ========== Helper Methods ==========

    private static User createComplexUser(String id, String username, String email) {
        User user = new User(id, username, email);

        // Address
        user.address = new Address(
            random.nextInt(9999) + " Main Street",
            "City " + random.nextInt(100),
            "State " + random.nextInt(50),
            String.format("%05d", random.nextInt(100000)),
            "Country"
        );
        user.address.additionalInfo.put("apartment", "Apt " + random.nextInt(500));
        user.address.additionalInfo.put("floor", String.valueOf(random.nextInt(50)));

        // Roles
        user.roles.addAll(Arrays.asList("USER", "MEMBER"));
        if (random.nextBoolean()) {
            user.roles.add("ADMIN");
        }

        // Preferences
        user.preferences.put("theme", random.nextBoolean() ? "dark" : "light");
        user.preferences.put("language", "en");
        user.preferences.put("notifications", Map.of(
            "email", true,
            "push", random.nextBoolean(),
            "sms", false
        ));
        user.preferences.put("dashboardWidgets", Arrays.asList("stats", "chart", "news"));

        // Orders
        int orderCount = 1 + random.nextInt(5);
        for (int o = 0; o < orderCount; o++) {
            Order order = new Order("order-" + id + "-" + o, id);

            int itemCount = 1 + random.nextInt(10);
            for (int i = 0; i < itemCount; i++) {
                OrderItem item = new OrderItem(
                    "prod-" + random.nextInt(1000),
                    "Product " + random.nextInt(100),
                    1 + random.nextInt(5),
                    10.0 + random.nextDouble() * 990.0
                );
                item.attributes.put("size", random.nextBoolean() ? "L" : "M");
                item.attributes.put("color", random.nextBoolean() ? "Blue" : "Red");
                order.items.add(item);
            }
            order.calculateTotal();

            order.payment = new PaymentInfo(
                random.nextBoolean() ? "CREDIT_CARD" : "PAYPAL",
                order.totalAmount,
                "USD"
            );
            order.payment.details.put("last4", String.format("%04d", random.nextInt(10000)));

            order.status = random.nextBoolean() ? "COMPLETED" : "SHIPPED";
            order.metadata.put("source", "web");
            order.metadata.put("ipAddress", "192.168.1." + random.nextInt(255));

            user.orderHistory.add(order);
        }

        return user;
    }

    private static String generateLongText(int length) {
        String[] words = {
            "Lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit",
            "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore",
            "magna", "aliqua", "enim", "ad", "minim", "veniam", "quis", "nostrud",
            "exercitation", "ullamco", "laboris", "nisi", "aliquip", "ex", "ea", "commodo",
            "consequat", "duis", "aute", "irure", "in", "reprehenderit", "voluptate",
            "velit", "esse", "cillum", "fugiat", "nulla", "pariatur", "excepteur", "sint",
            "occaecat", "cupidatat", "non", "proident", "sunt", "culpa", "qui", "officia",
            "deserunt", "mollit", "anim", "id", "est", "laborum", "cache", "performance",
            "java", "jcache", "caffeine", "benchmark", "optimization", "memory", "storage"
        };

        StringBuilder sb = new StringBuilder(length + 50);
        while (sb.length() < length) {
            sb.append(words[random.nextInt(words.length)]).append(" ");
            if (random.nextInt(15) == 0) {
                sb.append(". ");
            }
            if (random.nextInt(50) == 0) {
                sb.append("\n\n");
            }
        }
        return sb.substring(0, Math.min(sb.length(), length));
    }

    private static <V> CaffeineCache<String, V> createCache(String name, Class<V> valueType) {
        YmsConfiguration<String, V> config = new YmsConfiguration<>();
        config.setTypes(String.class, valueType);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);
        return new CaffeineCache<>(name, null, config);
    }

    @SuppressWarnings("unchecked")
    private static CaffeineCache<String, Map<String, Object>> createMapCache(String name) {
        YmsConfiguration<String, Map<String, Object>> config = new YmsConfiguration<>();
        config.setTypes(String.class, (Class<Map<String, Object>>) (Class<?>) Map.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        return new CaffeineCache<>(name, null, config);
    }

    @SuppressWarnings("unchecked")
    private static CaffeineCache<String, List<Object>> createListCache(String name) {
        YmsConfiguration<String, List<Object>> config = new YmsConfiguration<>();
        config.setTypes(String.class, (Class<List<Object>>) (Class<?>) List.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        return new CaffeineCache<>(name, null, config);
    }

    private static void printHeader(String title) {
        System.out.println("\n" + "-".repeat(70));
        System.out.println(title);
        System.out.println("-".repeat(70));
    }

    private static void printJsonLike(Map<String, Object> map, int indent) {
        String prefix = "  ".repeat(indent);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                System.out.println(prefix + "  " + entry.getKey() + ": {");
                printJsonLike((Map<String, Object>) value, indent + 1);
                System.out.println(prefix + "  }");
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    System.out.println(prefix + "  " + entry.getKey() + ": [");
                    for (Object item : list) {
                        System.out.println(prefix + "    {");
                        printJsonLike((Map<String, Object>) item, indent + 2);
                        System.out.println(prefix + "    }");
                    }
                    System.out.println(prefix + "  ]");
                } else {
                    System.out.println(prefix + "  " + entry.getKey() + ": " + list);
                }
            } else {
                System.out.println(prefix + "  " + entry.getKey() + ": " + value);
            }
        }
    }

    private static void printBenchmarkResults(String name, int count, long writeTime,
                                              long readSeqTime, long readRandomTime) {
        double writeMs = writeTime / 1_000_000.0;
        double readSeqMs = readSeqTime / 1_000_000.0;
        double readRandomMs = readRandomTime / 1_000_000.0;

        System.out.printf("Results (%,d records):%n", count);
        System.out.printf("  Write:       %.2f ms (%.2f ops/sec)%n",
            writeMs, count * 1_000_000_000.0 / writeTime);
        System.out.printf("  Read (seq):  %.2f ms (%.2f ops/sec)%n",
            readSeqMs, count * 1_000_000_000.0 / readSeqTime);
        System.out.printf("  Read (rand): %.2f ms (%.2f ops/sec)%n",
            readRandomMs, count * 1_000_000_000.0 / readRandomTime);
    }
}
