我在设计用Caffeine实现java的JCache规范的缓存，该缓存需要完整实现JCache规范，下面是我的设计文档，请你补充细节：

* 设计目标
* 总体思路与架构
* 关键机制的设计原理
* 必须注意的规范语义与工程风险

你可以把它当作 **内部设计评审文档 / 技术方案说明**。

---

# 基于 Caffeine 实现 JCache（JSR-107）的设计方案

## 1. 设计背景与目标

### 1.1 背景

Caffeine 是一个高性能、近乎最优的 Java 本地缓存库，但其语义模型与 JCache（JSR-107）存在显著差异：

* Caffeine 关注 **性能与并发**
* JCache 关注 **规范化语义与一致性**

二者在以下方面存在根本不一致：

* 过期模型
* 值存储语义（by-value）
* 事件模型
* 统计口径
* `Cache#invoke` 的原子语义

因此，**不能直接把 Caffeine 暴露为 JCache 实现**，而必须在其之上构建一层“规范适配层”。

---

### 1.2 设计目标

本方案目标是：

1. **严格遵循 JCache 规范语义**
2. **最大化复用 Caffeine 的高性能数据结构**
3. **不侵入 Caffeine 内核**
4. **通过结构性设计而非“打补丁”实现语义兼容**

---

## 2. 总体设计思路

### 2.1 分层模型

```
┌─────────────────────────────┐
│        JCache API 层         │
│  (Cache / EntryProcessor)   │
└─────────────▲───────────────┘
              │ 语义适配
┌─────────────┴───────────────┐
│        JCache 适配层         │
│  过期 / 统计 / 事件 / invoke │
└─────────────▲───────────────┘
              │ 数据存储
┌─────────────┴───────────────┐
│        Caffeine Cache        │
│     高性能并发 Map / 驱逐    │
└─────────────────────────────┘
```

**核心原则**：

> *Caffeine 只负责“存什么、怎么快”，
> JCache 适配层负责“语义是什么”。*

---

## 3. 数据模型设计

### 3.1 为什么不能直接存 Value

JCache 要求：

* 每个 entry 具有独立的过期时间
* 过期时间随操作类型变化（create / update / access）

而 Caffeine 的默认模型是：

* 过期策略多为 cache 级
* 不区分操作语义

因此必须引入**包装对象**。

---

### 3.2 Expirable Entry 模型

设计一个 **Entry 包装模型**：

```
Expirable<V>
 ├─ value         实际值
 └─ expireTime    绝对过期时间
```

**设计要点**：

* 过期时间是 **entry 级别**
* 使用绝对时间戳，避免多次换算
* Caffeine 内部存储的是 Expirable，而非原始值

---

## 4. 过期策略设计

### 4.1 规范约束

JCache 定义三类过期行为：

| 操作 | 过期策略                 |
| -- | -------------------- |
| 创建 | getExpiryForCreation |
| 更新 | getExpiryForUpdate   |
| 访问 | getExpiryForAccess   |

并且：

* 返回 `null` → 不修改过期时间
* 返回 `Duration.ZERO` → 立即过期
* 支持永久不过期

---

### 4.2 设计原则

1. **过期计算在适配层完成**
2. **不依赖 Caffeine 的原生过期机制**
3. **采用“惰性过期（lazy expiration）”**

---

### 4.3 过期处理流程

以 `get` 操作为例：

1. 从 Caffeine 获取 entry
2. 判断是否过期
3. 若过期：

    * 从缓存移除
    * 触发 EXPIRED 事件
    * 计为 miss
4. 若未过期：

    * 按 access 规则更新过期时间
    * 返回值
r
---

### 4.4 注意事项

⚠️ **不要尝试把 JCache 过期映射为 Caffeine Expiry**

原因：

* JCache 的过期是“语义驱动”
* Caffeine 的过期是“访问驱动”
* invoke 场景无法正确映射

---

## 5. 值存储语义（Copier 设计）

### 5.1 规范要求

JCache 默认要求：

* **store-by-value**
* cache 内部对象不得被外部修改
* listener 不得获得内部引用

---

### 5.2 设计方案

引入 **Copier 抽象**：

```
Copier<T>
 └─ copy(T value)
```

并支持两种策略：

| 策略                | 场景                 |
| ----------------- | ------------------ |
| IdentityCopier    | storeByValue=false |
| SerializingCopier | 默认策略               |

---

### 5.3 Copier 使用时机

| 场景             | 是否 copy |
| -------------- | ------- |
| put / putAll   | 必须      |
| get / iterator | 必须      |
| EntryListener  | 必须      |
| invoke entry   | 必须      |

---

### 5.4 注意事项

⚠️ 不得绕过 copier 直接暴露内部 value
⚠️ invoke 中的 MutableEntry 也必须遵循 by-value 语义

---

## 6. 统计信息设计

### 6.1 设计原则

* 统计口径 **以 JCache 语义为准**
* 不复用 Caffeine 的统计信息

---

### 6.2 统计项

* hits / misses
* puts / removals
* evictions
* average times（可选）

---

### 6.3 关键注意点

* 命中但过期 → miss
* invoke 内部操作不得重复计数
* clear 不应计入 eviction

---

## 7. 事件通知设计

### 7.1 规范事件类型

* CREATED
* UPDATED
* REMOVED
* EXPIRED

---

### 7.2 设计原则

1. 事件在 **语义层触发**
2. 不依赖 Caffeine 的 eviction listener
3. 事件顺序严格遵循规范

---

### 7.3 事件触发点

| 操作      | 事件                |
| ------- | ----------------- |
| put     | CREATED / UPDATED |
| remove  | REMOVED           |
| get（过期） | EXPIRED           |
| invoke  | 组合事件              |

---

### 7.4 注意事项

⚠️ invoke 可能产生多个事件
⚠️ oldValue / newValue 必须是 copy 后的值

---

## 8. invoke（EntryProcessor）设计

### 8.1 规范要求

* 对单 key 的 **原子操作**
* 期间不得被其他操作破坏一致性
* 允许读，但修改需序列化

---

### 8.2 设计思路

* 以 **entry 为粒度加锁**
* 构造逻辑上的 MutableEntry
* 处理完成后统一提交变更

---

### 8.3 注意事项

⚠️ 不直接使用 Caffeine compute
⚠️ invoke 中的 get / set / remove 必须延迟生效
⚠️ 过期与事件在 invoke 结束时统一处理

---

## 9. 并发与一致性约束

* JCache 并不要求全局强一致
* 但要求：

    * 单 key invoke 原子
    * 事件顺序正确
    * 统计口径一致

⚠️ 不要过度同步，避免破坏 Caffeine 并发优势

---

## 10. 总结

### 10.1 核心设计结论

> **实现 JCache 的关键，不在于缓存本身，而在于“语义模拟层”。**

| 维度     | 设计结论     |
| ------ | -------- |
| 存储     | Caffeine |
| 语义     | 适配层      |
| 过期     | Entry 级  |
| 值语义    | Copier   |
| 统计     | 语义统计     |
| 事件     | 显式触发     |
| invoke | 手工原子区    |

---


不需要支持注解，去掉@CacheResult
不需要单元测试
不需要提供自定义 Copier 接口扩展
不使用锁，而是使用Caffeine自身asMap提供的线程安全操作
补充过期数据驱逐细节，并且驱逐过期数据需要计入eviction
getAll、removeAll等批量操作不要求原子性
补充read-through细节
不需要invoke 死锁检测
不需要Spring 集成
增加YmsConfiguration类继承MutableConfiguration，提供缓存条目大小设置


----- 
CaffeineCache需要做比较大的优化:1、getAll、removeAll等批量操作不要求原子性，指的是批量操作本身可以不是原子性的，但是单个操作是必须原子性的，现在的代码是有并发操作bug的，此外批量操作尽量使用批量模式，以提高性能；2、规范里的某些方法没有实现；3、write-through，应当是先write外部，按规范外部write时需要捕捉异常，并去掉外部write失败的key，继续处理外部write成功的key







