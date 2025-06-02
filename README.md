# Custom Thread Pool

## 1. Описание
Реализован собственный пул потоков с:
- `corePoolSize`, `maxPoolSize`, `keepAliveTime`, `queueSize`, `minSpareThreads`
- Политикой отказа (по умолчанию `CallerRunsPolicy`)
- Логированием: создание/завершение потоков, постановка/отказ задач, запуск/окончание задач, таймауты.

Интерфейс:
```java
interface CustomExecutor extends Executor {
    <T> Future<T> submit(Callable<T> callable);
    void shutdown();
    void shutdownNow();
}
