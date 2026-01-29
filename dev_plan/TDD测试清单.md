# TDD 测试清单（JCache 适配层）

> 目的：确保所有 JCache 语义在实现前由测试定义并验证。
> 基于：JSR-107详细设计方案.md
> 参考：完整的 TDD 测试用例模板.md
---

## A. 基础语义与 CRUD

### A.1 get
- [ ] get：命中返回正确值
- [ ] get：未命中返回 null
- [ ] get：过期条目返回 null
- [ ] get：命中时更新 access expiry（若配置）
- [ ] get：key 为 null 抛出 NullPointerException

### A.2 put
- [ ] put：创建新条目
- [ ] put：覆盖已存在条目（更新）
- [ ] put：覆盖过期条目（视为创建）
- [ ] put：创建时计算 creation expiry
- [ ] put：更新时计算 update expiry
- [ ] put：key 为 null 抛出 NullPointerException
- [ ] put：value 为 null 抛出 NullPointerException

### A.3 putAll
- [ ] putAll：多 key 创建
- [ ] putAll：多 key 更新（部分存在）
- [ ] putAll：整体非原子但单 key 原子
- [ ] putAll：逐 key 触发事件（不合并）
- [ ] putAll：逐 key 计数统计（不合并）

### A.4 putIfAbsent
- [ ] putIfAbsent：key 不存在时写入并返回 true
- [ ] putIfAbsent：key 已存在时不写入并返回 false
- [ ] putIfAbsent：key 已存在计 hit，不计 put
- [ ] putIfAbsent：key 不存在计 miss + put
- [ ] putIfAbsent：过期条目视为不存在

### A.5 getAndPut
- [ ] getAndPut：命中返回旧值并更新
- [ ] getAndPut：未命中返回 null 并创建
- [ ] getAndPut：writer 失败不写缓存，抛 CacheWriterException
- [ ] getAndPut：事件 CREATED/UPDATED 与统计 hit/put 正确

### A.6 getAndRemove
- [ ] getAndRemove：命中返回旧值并删除
- [ ] getAndRemove：未命中返回 null（不计统计）
- [ ] getAndRemove：writer 失败不删除，抛 CacheWriterException
- [ ] getAndRemove：REMOVED 事件与 removal 统计正确

### A.7 replace / replace(k, old, new)
- [ ] replace：仅命中时更新，不存在不计统计
- [ ] replace(k, old, new)：仅 old 匹配才更新
- [ ] replace：writer 失败不更新，抛 CacheWriterException
- [ ] replace：UPDATED 事件与统计正确

### A.8 remove(k, v)
- [ ] remove(k, v)：value 匹配才删除
- [ ] remove(k, v)：不匹配不计统计
- [ ] remove(k, v)：writer 失败不删除，抛 CacheWriterException

### A.9 removeAll
- [ ] removeAll(Set)：删除指定 key 集合
- [ ] removeAll()：删除所有条目
- [ ] removeAll：整体非原子但单 key 原子
- [ ] removeAll：逐 key 触发 REMOVED 事件

### A.10 clear
- [ ] clear：清空所有条目
- [ ] clear：计 removal 统计
- [ ] clear：不计 eviction 统计
- [ ] clear：不触发逐条事件

---

## B. 数据模型与 By-Value 语义

### B.1 Expirable 包装
- [ ] Expirable：正确存储 value 和 expireTime
- [ ] Expirable.isExpired()：正确判断过期状态
- [ ] Expirable.withExpireTime()：创建新对象而非修改原对象（不可变）

### B.2 By-Value 规则
- [ ] storeByValue=true：put 时复制 value
- [ ] storeByValue=true：get 返回值为复制品
- [ ] storeByValue=true：修改原对象不影响缓存
- [ ] storeByValue=false：使用 identity copy
- [ ] storeByValue=false：返回原对象引用

### B.3 GC 优化验证
- [ ] 过期时间未变化时复用原 Expirable 对象
- [ ] Duration.ETERNAL 条目正确使用 Long.MAX_VALUE 语义
- [ ] Duration.ZERO 条目逻辑不可见

---

## C. 过期与 Eviction

### C.1 过期策略
- [ ] ExpiryPolicy：null 返回值不修改过期时间
- [ ] Duration.ZERO：立即过期（逻辑不可见）
- [ ] Duration.ETERNAL：永不过期
- [ ] creation expiry：创建时正确计算
- [ ] update expiry：更新时正确计算
- [ ] access expiry：访问时正确计算

### C.2 惰性清理触发路径
- [ ] get：触发过期清理
- [ ] getAll：触发过期清理
- [ ] containsKey：触发过期清理
- [ ] iterator：触发过期清理
- [ ] invoke：触发过期清理
- [ ] put：先校验旧值过期再写入
- [ ] replace：先校验过期再执行
- [ ] remove：先校验过期再执行

### C.3 过期驱逐计数
- [ ] 过期命中视为 miss
- [ ] 过期触发 EXPIRED 事件
- [ ] 过期清理计入 eviction 统计
- [ ] SIZE/WEIGHT 驱逐计入 eviction（不触发事件）

---

## D. 事件语义

### D.1 事件类型覆盖
- [ ] CREATED：新条目创建时触发
- [ ] UPDATED：已存在条目更新时触发
- [ ] REMOVED：显式删除时触发
- [ ] EXPIRED：过期移除时触发

### D.2 事件值语义
- [ ] oldValue 为 by-value 复制品
- [ ] newValue 为 by-value 复制品
- [ ] 事件顺序严格遵循规范

### D.3 RemovalCause → JCache 事件映射
- [ ] EXPIRED → EXPIRED 事件
- [ ] EXPLICIT → REMOVED 事件
- [ ] REPLACED → UPDATED 事件
- [ ] SIZE/WEIGHT → 不触发事件（仅计 eviction）

### D.4 EventPolicy 默认策略
- [ ] 默认同步派发（调用线程内执行）
- [ ] listener 异常被捕获并记录
- [ ] listener 异常不影响主流程
- [ ] listener 异常不回滚状态变更

### D.5 invoke 中组合事件
- [ ] invoke 执行 setValue 触发 CREATED/UPDATED
- [ ] invoke 执行 remove 触发 REMOVED
- [ ] invoke 中事件顺序正确

---

## E. 统计口径

### E.1 基础统计
- [ ] hit：命中时正确计数
- [ ] miss：未命中时正确计数
- [ ] put：写入时正确计数
- [ ] removal：删除时正确计数
- [ ] eviction：驱逐时正确计数（含过期）

### E.2 统计更新时机
- [ ] miss 在确认不存在/过期时立即计数
- [ ] 统计仅在最终状态确认后累加
- [ ] 异常路径走独立计数器（loadFailure/writeFailure）

### E.3 边界情况统计
- [ ] invoke 内不重复计数
- [ ] clear 计 removal，不计 eviction
- [ ] getAndRemove 缺失时不计任何统计
- [ ] replace 缺失时不计任何统计
- [ ] putIfAbsent 命中计 hit、不计 put
- [ ] putIfAbsent 缺失计 miss + put
- [ ] iterator 不计 hit/miss
- [ ] containsKey 不计 hit/miss

### E.4 异常计数器
- [ ] loadFailure：read-through 调用 loader 抛异常时计数
- [ ] writeFailure：write-through 调用 writer 抛异常时计数
- [ ] loadDiscarded：load 成功但写入前被并发更新丢弃时计数
- [ ] 异常计数器与主统计互斥

---

## F. Read-through（CacheLoader）

### F.1 基础功能
- [ ] miss 触发 load
- [ ] 过期触发 load
- [ ] load 返回 null 不写入缓存
- [ ] load 返回非 null 写入缓存
- [ ] load 异常抛出 CacheLoaderException

### F.2 getAll read-through
- [ ] getAll 触发 loadAll（如可用）
- [ ] getAll 部分命中时仅 load 缺失 key
- [ ] getAll read-through 成功写入触发 CREATED 事件

### F.3 并发约束
- [ ] load 在 compute 外执行
- [ ] 写入前二次校验当前值
- [ ] 发现并发更新时丢弃 load 结果
- [ ] 丢弃时返回当前新值（非 load 结果）
- [ ] 丢弃时 miss 计数保持（已计）
- [ ] 丢弃时不计 put
- [ ] 丢弃时记录 loadDiscarded

### F.4 副作用顺序
- [ ] 顺序：load → cache put → event(CREATED) → stats → return

---

## G. Write-through（CacheWriter）

### G.1 单 key 操作
- [ ] put：先写外部再写缓存
- [ ] remove：先写外部再删缓存
- [ ] 异常抛出 CacheWriterException
- [ ] 异常时不写入/删除缓存
- [ ] 异常时不触发事件

### G.2 批量操作
- [ ] 批量写：成功 key 生效、失败 key 不生效
- [ ] 批量异常必须包含失败明细（key + 原因）
- [ ] 部分成功不回滚已成功 key

### G.3 副作用顺序
- [ ] 顺序：writer → cache put/remove → event → stats → return
- [ ] writer 失败不写入 cache 且不触发事件
- [ ] writer 成功才计 put/removal

---

## H. 批量语义

### H.1 原子性
- [ ] getAll：整体非原子但单 key 原子
- [ ] putAll：整体非原子但单 key 原子
- [ ] removeAll：整体非原子但单 key 原子
- [ ] invokeAll：整体非原子但单 key 原子

### H.2 逐 key 语义一致
- [ ] 批量操作事件按 key 逐条触发（不合并）
- [ ] 批量操作统计按 key 逐条计数（不合并）
- [ ] 批量过期判断逐 key 执行
- [ ] 批量 read-through 语义等价逐 key（可批量优化）
- [ ] 批量 write-through 语义等价逐 key（可批量优化）

### H.3 部分失败处理
- [ ] 部分成功可见性：不回滚已成功 key
- [ ] 禁止共享可变中间态（避免跨 key 干扰）

---

## I. 并发与原子性

### I.1 单 key 原子语义
- [ ] 单 key 并发读写一致性
- [ ] 使用 asMap() 原子操作（compute/computeIfPresent/putIfAbsent/remove）
- [ ] 禁止 check-then-act 造成竞态

### I.2 原子边界验证
- [ ] compute 内仅状态判断 + 值决策 + 状态转换
- [ ] compute 内无 I/O 操作
- [ ] compute 内无 loader/writer 调用
- [ ] compute 内无事件触发
- [ ] compute 内无统计累计

### I.3 invoke（EntryProcessor）原子语义
- [ ] invoke 单 key 原子
- [ ] MutableEntry 为逻辑态，get/set/remove 延迟提交
- [ ] processor 执行完毕后状态才生效

### I.4 invoke read-through 两阶段
- [ ] 第一次 compute：判定是否需要 read-through
- [ ] compute 外执行 load
- [ ] 第二次 compute：携带 load 结果执行 processor
- [ ] 二阶段并发丢弃：使用当前缓存值（非 load 结果）

### I.5 EntryProcessor 再入禁止
- [ ] EntryProcessor 内调用 cache.get() 必须失败
- [ ] EntryProcessor 内调用 cache.put() 必须失败
- [ ] EntryProcessor 内调用 cache.remove() 必须失败
- [ ] EntryProcessor 内调用 cache.invoke() 必须失败
- [ ] 检测到 reentrant 调用抛出 CacheException

### I.6 containsKey 并发
- [ ] containsKey 与 get 并发一致性

---

## J. 迭代器语义

- [ ] iterator 遍历所有未过期条目
- [ ] iterator 遇过期条目触发 EXPIRED 事件
- [ ] iterator 遇过期条目计 eviction
- [ ] iterator 不计 hit/miss
- [ ] iterator 返回 by-value 复制品
- [ ] iterator.remove() 正确删除条目

---

## K. 配置与限制（YmsConfiguration）

### K.1 容量配置
- [ ] maximumSize 生效
- [ ] maximumWeight 生效
- [ ] maximumSize 与 maximumWeight 互斥验证
- [ ] maximumWeight 必须配 weigher 验证
- [ ] 未配置 weigher 设置 maximumWeight 抛异常

### K.2 storeByValue 配置
- [ ] storeByValue=true 行为正确
- [ ] storeByValue=false 行为正确

### K.3 其他配置
- [ ] expiryPolicy 正确应用
- [ ] read-through 配置正确应用
- [ ] write-through 配置正确应用
- [ ] statistics 开关正确应用

---

## L. 资源与生命周期

- [ ] close 后调用 get 抛 IllegalStateException
- [ ] close 后调用 put 抛 IllegalStateException
- [ ] close 后调用 remove 抛 IllegalStateException
- [ ] close 后调用 invoke 抛 IllegalStateException
- [ ] isClosed 返回正确状态
- [ ] getConfiguration 返回一致配置
- [ ] getName 返回正确名称
- [ ] getCacheManager 返回正确 manager
- [ ] unwrap 正确返回底层实现

---

## M. 异常语义

### M.1 异常类型
- [ ] read-through 失败抛 CacheLoaderException
- [ ] write-through 失败抛 CacheWriterException
- [ ] CacheWriterException 包含失败明细
- [ ] EntryProcessor 异常抛 EntryProcessorException
- [ ] reentrant 违规抛 CacheException
- [ ] key 为 null 抛 NullPointerException
- [ ] value 为 null 抛 NullPointerException

### M.2 异常不回滚
- [ ] processor 异常不回滚已提交变更
- [ ] 已提交变更按正常统计归属

---

## N. 异常计数与统计归属

- [ ] loadFailure 与主统计互斥，不计 put
- [ ] writeFailure 与主统计互斥，不计 put/removal
- [ ] loadDiscarded 不计 put
- [ ] writer 成功才计 put/removal
- [ ] loader 成功写入才计 put

---

## O. API 覆盖矩阵验证

> 验证 12 章 API 覆盖矩阵中所有组合

### O.1 Read-path API
| API | 单 key 原子 | 过期更新 | 事件 | 统计 | read-through |
|-----|------------|---------|------|------|-------------|
| get | [ ] | [ ] access | [ ] EXPIRED/CREATED | [ ] hit/miss | [ ] 是 |
| getAll | [ ] 每 key | [ ] access | [ ] EXPIRED/CREATED | [ ] hit/miss | [ ] 是 |
| containsKey | [ ] | [ ] access | [ ] EXPIRED | [ ] 不计 | [ ] 否 |

### O.2 Write-path API
| API | 单 key 原子 | 过期更新 | 事件 | 统计 | write-through |
|-----|------------|---------|------|------|--------------|
| put | [ ] | [ ] create/update | [ ] CREATED/UPDATED | [ ] put | [ ] 是 |
| putAll | [ ] 每 key | [ ] create/update | [ ] CREATED/UPDATED | [ ] put | [ ] 是 |
| putIfAbsent | [ ] | [ ] create | [ ] CREATED | [ ] put/miss | [ ] 是 |
| getAndPut | [ ] | [ ] update | [ ] CREATED/UPDATED | [ ] hit/put | [ ] 是 |
| getAndRemove | [ ] | [ ] n/a | [ ] REMOVED | [ ] hit/removal | [ ] 是 |
| replace | [ ] | [ ] update | [ ] UPDATED | [ ] hit/put | [ ] 是 |
| replace(k,old,new) | [ ] | [ ] update | [ ] UPDATED | [ ] hit/put | [ ] 是 |
| remove | [ ] | [ ] n/a | [ ] REMOVED | [ ] removal | [ ] 是 |
| remove(k,v) | [ ] | [ ] n/a | [ ] REMOVED | [ ] removal | [ ] 是 |
| removeAll | [ ] 每 key | [ ] n/a | [ ] REMOVED | [ ] removal | [ ] 是 |
| clear | [ ] 否 | [ ] n/a | [ ] 不触发 | [ ] removal | [ ] 是 |

### O.3 Invoke-path API
| API | 单 key 原子 | 过期更新 | 事件 | 统计 | read-through | write-through |
|-----|------------|---------|------|------|-------------|--------------|
| invoke | [ ] | [ ] create/update/access | [ ] CREATED/UPDATED/REMOVED/EXPIRED | [ ] put/removal/hit/miss | [ ] 两阶段 | [ ] 视操作 |
| invokeAll | [ ] 每 key | [ ] create/update/access | [ ] 同 invoke | [ ] 同 invoke | [ ] 两阶段 | [ ] 视操作 |

---

## P. 风险验证测试

> 基于 13 章工程风险定义

### P.1 内存滞留风险
- [ ] 长期不访问的过期条目不会无限增长（需业务触发清理）
- [ ] 惰性清理在访问时正确执行

### P.2 原子边界风险
- [ ] 副作用不在 compute 内（防止不可测时序）
- [ ] 事件/统计/loader/writer 均在 compute 之后执行

### P.3 write-through 批量失败
- [ ] 批量失败时性能开销可接受
- [ ] 失败明细完整准确

### P.4 by-value 复制失败
- [ ] 序列化失败时异常正确传播
- [ ] 反序列化失败时异常正确传播

---

## Q. 副作用顺序矩阵验证

> 验证 9.2 章 API 级副作用顺序

### Q.1 Read-path 顺序
- [ ] get (miss+read-through): load → cache put → event(CREATED) → stats
- [ ] get (hit): stats(hit)
- [ ] get (expired): event(EXPIRED) → stats(miss)
- [ ] getAll: per-key 同 get

### Q.2 Write-path 顺序
- [ ] put: writer → cache put → event(CREATED/UPDATED) → stats(put)
- [ ] putIfAbsent: writer → cache put → event(CREATED) → stats(put)
- [ ] getAndPut: writer → cache put → event → stats(hit/put)
- [ ] replace: writer → cache put → event(UPDATED) → stats(hit/put)
- [ ] remove: writer → cache remove → event(REMOVED) → stats(removal)
- [ ] getAndRemove: writer → cache remove → event(REMOVED) → stats(hit/removal)
- [ ] clear: writer → cache clear → stats(removal)

### Q.3 Invoke-path 顺序
- [ ] invoke: compute标记 → load(可选) → compute执行 → event/stats

---

## Z. API 覆盖补全清单（与设计方案对齐）

### Z.1 getAndPut
- [ ] getAndPut：命中返回旧值并更新
- [ ] getAndPut：未命中返回 null 并创建
- [ ] getAndPut：writer 失败不写缓存，抛 CacheWriterException
- [ ] getAndPut：事件 CREATED/UPDATED 与统计 hit/put 正确

### Z.2 getAndRemove
- [ ] getAndRemove：命中返回旧值并删除
- [ ] getAndRemove：未命中返回 null（不计统计）
- [ ] getAndRemove：writer 失败不删除，抛 CacheWriterException
- [ ] getAndRemove：REMOVED 事件与 removal 统计正确

### Z.3 replace / replace(k, old, new)
- [ ] replace：仅命中时更新，不存在不计统计
- [ ] replace(k, old, new)：仅 old 匹配才更新
- [ ] replace：writer 失败不更新，抛 CacheWriterException
- [ ] replace：UPDATED 事件与统计正确

### Z.4 remove(k, v)
- [ ] remove(k, v)：value 匹配才删除
- [ ] remove(k, v)：不匹配不计统计
- [ ] remove(k, v)：writer 失败不删除，抛 CacheWriterException

### Z.5 invoke / invokeAll
- [ ] invoke：两阶段 read-through（如需）顺序正确
- [ ] invoke：并发丢弃策略生效（使用当前值）
- [ ] invoke：processor 异常抛 EntryProcessorException
- [ ] invokeAll：逐 key 语义与逐 key 失败
- [ ] invoke：事件/统计与最终状态一致

### Z.6 getAll / removeAll
- [ ] getAll：read-through 写入触发 CREATED
- [ ] getAll：逐 key 事件与统计，不合并
- [ ] removeAll：逐 key writer/事件/统计
- [ ] removeAll：部分失败不回滚成功 key

### Z.7 iterator / containsKey
- [ ] iterator：不计 hit/miss；遇过期触发 EXPIRED 与 eviction
- [ ] iterator.remove()：触发 REMOVED 与 removal
- [ ] containsKey：不计 hit/miss，不触发事件（仅惰性清理）

### Z.8 clear / close / isClosed / getConfiguration / unwrap
- [ ] clear：write-through 成功后计 removal，不触发逐条事件
- [ ] clear：writer 失败抛 CacheWriterException
- [ ] close：关闭后所有操作抛 IllegalStateException
- [ ] isClosed：关闭状态正确
- [ ] getConfiguration：返回配置视图正确
- [ ] unwrap：支持/不支持的类型行为符合规范

### Z.9 配置与 by-value
- [ ] storeByValue=true：copy 语义生效
- [ ] storeByValue=false：identity 语义
- [ ] maximumSize 与 maximumWeight 互斥校验
- [ ] maximumWeight 无 weigher 抛异常
