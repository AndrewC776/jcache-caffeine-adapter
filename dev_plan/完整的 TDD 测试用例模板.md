# 完整的 TDD 测试用例模板

> 目的：用于在实现前以测试驱动方式描述 JCache 语义。

---

## 1. 用例元信息
- 用例编号：
- 用例名称：
- 关联 API：
- 优先级：P0 / P1 / P2
- 覆盖语义：过期 / 事件 / 统计 / read-through / write-through / 并发 / 批量
- 依赖策略：ExpirationPolicy / ReadThroughPolicy / WriteThroughPolicy / BatchPolicy / EventPolicy / StatsPolicy
- 规范约束覆盖（必选多项）：
  - 原子边界：compute 内无 I/O/loader/writer/event/stats
  - 副作用顺序：read-through 或 write-through 顺序
  - 过期与 eviction：过期是否计 eviction
  - 事件隔离：listener 异常是否影响主流程
  - 统计口径：hit/miss/put/removal/eviction 与异常计数器
  - EntryProcessor 两阶段与并发丢弃（如适用）

---

## 2. 前置条件（Given）
- Cache 配置：
  - storeByValue：
  - maximumSize / maximumWeight：
  - weigher：
  - expiryPolicy：
  - read-through：
  - write-through：
- 初始数据：
- 时间点（若涉及过期）：
- 事件监听器：
- 统计开关：
- 事件策略约束：
  - isSynchronous：
  - oldValueRequired：
  - filter：
- 线程模型（如并发/两阶段/回调）：
  - 线程数：
  - 关键交叉点：

---

## 3. 操作步骤（When）
- 操作序列：
  1.
  2.
  3.
- 并发模型：
  - 线程数：
  - 关键交叉点：
- 原子边界验证（必填）：
  - compute 内是否包含 I/O/loader/writer/event/stats：
  - compute 内仅做状态判断 + 值决策 + 状态转换：
- 副作用顺序验证（必填）：
  - read-through：load → cache put → event → stats → return
  - write-through：writer → cache put/remove → event → stats → return

---

## 4. 期望结果（Then）

### 4.1 数据结果
- 期望缓存内值：
- 期望返回值：
- 期望过期时间：

### 4.2 事件
- 期望事件序列：
- 事件 oldValue/newValue 断言：
- listener 异常隔离：
  - 异常是否被捕获并记录：
  - 异常是否影响主流程：

### 4.3 统计
- hits：
- misses：
- puts：
- removals：
- evictions：
- 异常计数器（必填）：
  - loadFailure：
  - writeFailure：
  - loadDiscarded：

### 4.4 读写穿透
- read-through 是否触发：
- write-through 是否触发：
- 失败明细：
- 并发丢弃语义（如适用）：
  - load 成功但被并发更新丢弃：
  - 返回值是否为当前缓存值（非 load 结果）：

### 4.5 异常
- 期望异常类型：
- 异常消息：

---

## 5. 边界条件
- null 值处理：
- Duration.ZERO / ETERNAL：
- 重复执行：
- 过期清理触发路径（必填）：get/getAll/containsKey/iterator/invoke/put/replace/remove

---

## 6. 可追踪性
- 关联需求/规范条款：
- 关联风险项：
- 关联缺陷（如有）：

---

## 7. 备注
- 其他补充说明：

