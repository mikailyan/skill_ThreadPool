# Custom Thread Pool

## Описание
Реализован собственный пул потоков с:
- `corePoolSize`, `maxPoolSize`, `keepAliveTime`, `queueSize`, `minSpareThreads`
- Политикой отказа (по умолчанию `CallerRunsPolicy`)
- Логированием: создание/завершение потоков, постановка/отказ задач, запуск/окончание задач, таймауты.

## Производительность
Стандартный ThreadPoolExecutor (JDK) и CustomThreadPool::
- JDK-пул оптимизирован на CAS и внутренние структуры, наш пул чуть медленнее при частом создании/завершении потоков.
- При стабильной нагрузке (несколько десятков потоков) разница ≪ 1 ms.
- Для HTTP-серверов (Tomcat/Jetty) специализированные пулы эффективнее за счёт адаптивного масштабирования и NIO-интеграции.

## Подбор параметров
CPU-bound задачи:
- corePoolSize ≈ number_of_cores
- maxPoolSize = corePoolSize
- queueSize «обычная» (10–100), чтобы не нарастить долгий backlog.
- keepAliveTime ≈ 10–30 сек, minSpareThreads = corePoolSize (ядро не убивать).

I/O-bound задачи::
- corePoolSize ≈ number_of_cores
- maxPoolSize ≈ cores * (1 + W/C) (W = время ожидания I/O, C = время вычислений).
- queueSize ≈ cores * 10 (или больше).
- keepAliveTime = 30–60 сек, minSpareThreads = 1–2.

Очередь задач:
- Малая (10–50): быстро «отказывается» при пике, низкая задержка.
- Большая (100+): гасит всплески, но увеличивает время ожидания.

## Краткие рекомендации
- Одна общая ArrayBlockingQueue<Runnable> taskQueue.
- При execute(...):
  - Пытаемся offer в очередь.
  - Если очередь полна и workers.size() < maxPoolSize, создаём новый поток.
  - Иначе вызываем политику отказа (по умолчанию CallerRunsPolicy).
- Worker:
```java
task = taskQueue.poll(keepAliveTime, TimeUnit.MILLISECONDS);
if (task != null) run(task);
else if (workers.size() > corePoolSize) terminate();
```
- Балансировка — за счёт единой очереди: первый освободившийся воркер берёт задачу.

## Распределение задач
- В 99 % случаев достаточно ThreadPoolExecutor(core, max, keepAlive, unit, queue, factory, handler).
- Если нужна специфичная логика отказа/балансировки — используйте CustomThreadPool.
- Перед деплоем провести нагрузочное тестирование:
  - Throughput: задачи Runnable/Callable с разным временем выполнения.
  - Измерить Time-to-Start (от submit до run()).
  - Поменять corePoolSize, maxPoolSize, queueSize, keepAliveTime и сравнить результаты.
