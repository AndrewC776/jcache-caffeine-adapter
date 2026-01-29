# Caffeine JCache Adapter

A JCache (JSR-107) implementation backed by Caffeine, providing a standards-compliant cache adapter with lazy expiration, event dispatching, statistics, and read/write-through support.

## Highlights
- JCache API compatibility with Caffeine as the storage engine
- Lazy expiration without background cleanup threads
- By-value semantics via internal Copier abstraction
- Event dispatching for CREATED/UPDATED/REMOVED/EXPIRED
- Cache statistics (hits, misses, puts, removals, evictions)
- Read-through and write-through integration

## Project Structure
- `src/main/java/com/yms/cache`: core cache implementation and helpers
- `dev_plan/`: design and TDD plans
- `caffeine实现jcache设计文档.md`: design reference (Chinese)

## Build & Test
```bash
mvn test
```

## Design Notes
This project follows the design constraints documented in `dev_plan/JSR-107详细设计方案.md`, including single-key atomicity with Caffeine `asMap()` operations and strict separation of side effects from atomic state transitions.
