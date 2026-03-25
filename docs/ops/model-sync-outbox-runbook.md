# Model sync outbox — эксплуатация

## Назначение

При `arepos.model-sync.outbox-enabled=true` сообщения `model_changed` не отправляются из HTTP-потока сразу в STOMP, а пишутся в таблицу `model_sync_outbox` в той же транзакции, что и мутация модели. Воркер `ModelSyncOutboxScheduler` периодически читает неопубликованные строки и вызывает `SimpMessagingTemplate.convertAndSend`.

## Конфигурация

| Свойство / env | Смысл |
|----------------|--------|
| `arepos.model-sync.outbox-enabled` / `MODEL_SYNC_OUTBOX_ENABLED` | Включить outbox (по умолчанию `false`). |
| `arepos.model-sync.outbox-publish-interval-ms` / `MODEL_SYNC_OUTBOX_PUBLISH_MS` | Интервал тика воркера (мс). |
| `arepos.model-sync.outbox-batch-size` / `MODEL_SYNC_OUTBOX_BATCH_SIZE` | Размер пачки за тик. |

## Метрики (Prometheus)

- `arepos_model_sync_outbox_pending_rows` — оценка числа неопубликованных строк (обновляется на каждом тике воркера).
- `arepos_model_sync_outbox_retries_total` — неудачные попытки отправки.
- `arepos_model_sync_outbox_publish_failures_total` — строки, достигшие лимита попыток (25).

## Симптомы и действия

1. **Рост `pending_rows`, клиенты отстают**  
   Проверить брокер STOMP/WebSocket, логи `ModelSyncOutboxPublishService` (`model sync outbox publish failed`). Устранить сеть/брокер; воркер продолжит ретраи.

2. **Рост `publish_failures_total`**  
   Смотреть `last_error` в БД для строк с высоким `attempts`. Часто невалидный JSON в `payload` или недоступный брокер. После исправления можно вручную сбросить `attempts` / `last_error` для строки или удалить мусор (осторожно).

3. **Откат на прямую отправку**  
   Выставить `MODEL_SYNC_OUTBOX_ENABLED=false` и перезапустить приложение. Новые мутации пойдут напрямую; уже накопленные в outbox строки нужно либо допубликовать вручную (операционно), либо оставить воркеру до успеха.

## Примечание

Сообщения `diagram_live`, `diagram_pointer`, `diagram_spectators` по-прежнему отправляются **напрямую** (низкая задержка), не через outbox.
