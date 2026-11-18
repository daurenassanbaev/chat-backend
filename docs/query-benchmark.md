# getChats: было / DISTINCT ON / LATERAL

Команда: `./gradlew :message-service:queryBenchmark --rerun-tasks`

PostgreSQL 16 Alpine в Testcontainers, настройки по умолчанию.
1 000 000 сообщений, 100 чатов, выбраны 20 чатов, текст сообщения — 100 символов.
По 10 000 сообщений на чат; время сообщения растёт с его номером.
Данные создаются через generate_series в отдельном одноразовом контейнере.

Один прогрев и три EXPLAIN (ANALYZE, BUFFERS) для каждого варианта.
Все варианты проверены последовательно на одном наборе данных в одном контейнере.
Для «было» оставлены исходные индексы отдельных колонок; перед DISTINCT ON
и LATERAL добавлен существующий в проекте индекс
(chat_id, created_at DESC, id DESC). Настройки планировщика не менялись.
Замер касается только SQL: передача ответа и создание Java-объектов не измеряются.
Это локальные результаты на искусственных данных, а не обещание времени ответа API.

| Вариант | Возвращено строк | Три запуска, мс | Медиана, мс |
|---|---:|---|---:|
| Было | 200000 | 83.967 / 83.076 / 83.580 | 83.580 |
| DISTINCT ON | 20 | 157.703 / 160.594 / 155.615 | 157.703 |
| LATERAL | 20 | 0.048 / 0.039 / 0.038 | 0.039 |

## Что изменилось

Исходный запрос возвращал все 200 000 сообщений выбранных чатов,
после чего Java оставляла последнее для каждого чата.
DISTINCT ON возвращает 20 сообщений, но сначала обрабатывает большую историю;
конкретные чтения, сортировки и возможная запись на диск видны в планах ниже.
LATERAL для каждого из 20 chat_id находит первую запись в уже упорядоченном индексе
и останавливается после неё. Ограничение LIMIT 1 применяется отдельно к каждому чату.
В тесте проверяются использование idx_messages_chat_created_id и совпадение
выбранных сообщений с DISTINCT ON. Сам LATERAL взят из аннотации репозитория,
чтобы замер соответствовал запросу приложения.

Пустые чаты не дают строки сообщения: ChatService по-прежнему строит ответ
из списка чатов пользователя и подставляет null для отсутствующего сообщения.
При равном времени последняя запись выбирается по id DESC.
Преимущество проверено для 20 чатов с большой историей; для другого числа чатов
и объёма истории нужны отдельные измерения.

Было:
```sql
SELECT * FROM messages WHERE chat_id IN ('f316dbbe-85f1-4587-454d-bfe607cf01de'::uuid,'2a4b6190-231e-8db9-8848-f7b473257e60'::uuid,'c43f2786-f7ea-3a4d-fefc-c60fa991eb95'::uuid,'d27ec30c-da33-a219-4942-deea94d3bbdf'::uuid,'6ad09b69-20d7-5618-b847-f3b2af989faa'::uuid,'ed1ff957-78c9-7371-84ec-15c076827dd6'::uuid,'dcb23fb3-4811-8b8f-3df1-774b7a886b75'::uuid,'8d2dc946-11f2-5ed6-781a-c79aaba65e77'::uuid,'a64b508b-ddec-af19-06fb-249010e6fb6c'::uuid,'9d36ebd5-3ba5-f0df-324c-62a6040df813'::uuid,'7ca75b25-d8a1-013b-1ac5-b9fb4442d927'::uuid,'f9be77bd-b0e0-6958-c7da-b49d71e39fe6'::uuid,'3fffb420-28ad-bc26-6d1c-50f6cb46e172'::uuid,'f9921565-aaa9-8dea-46bd-d5fdf8655acf'::uuid,'0508079d-cea4-9d19-f74f-731888914ffa'::uuid,'1e645194-4089-2a86-2d39-85fdc0b09ef3'::uuid,'b6af08ec-562f-55bc-e26d-60ec81a2b525'::uuid,'502f8ebd-145a-3fb5-2568-abef04db2625'::uuid,'4557c514-d056-9f18-81de-73bf48425b20'::uuid,'526ed4b7-44ac-a61c-a0a7-f67737906010'::uuid) ORDER BY created_at DESC
```

DISTINCT ON:
```sql
SELECT DISTINCT ON (chat_id) * FROM messages WHERE chat_id IN ('f316dbbe-85f1-4587-454d-bfe607cf01de'::uuid,'2a4b6190-231e-8db9-8848-f7b473257e60'::uuid,'c43f2786-f7ea-3a4d-fefc-c60fa991eb95'::uuid,'d27ec30c-da33-a219-4942-deea94d3bbdf'::uuid,'6ad09b69-20d7-5618-b847-f3b2af989faa'::uuid,'ed1ff957-78c9-7371-84ec-15c076827dd6'::uuid,'dcb23fb3-4811-8b8f-3df1-774b7a886b75'::uuid,'8d2dc946-11f2-5ed6-781a-c79aaba65e77'::uuid,'a64b508b-ddec-af19-06fb-249010e6fb6c'::uuid,'9d36ebd5-3ba5-f0df-324c-62a6040df813'::uuid,'7ca75b25-d8a1-013b-1ac5-b9fb4442d927'::uuid,'f9be77bd-b0e0-6958-c7da-b49d71e39fe6'::uuid,'3fffb420-28ad-bc26-6d1c-50f6cb46e172'::uuid,'f9921565-aaa9-8dea-46bd-d5fdf8655acf'::uuid,'0508079d-cea4-9d19-f74f-731888914ffa'::uuid,'1e645194-4089-2a86-2d39-85fdc0b09ef3'::uuid,'b6af08ec-562f-55bc-e26d-60ec81a2b525'::uuid,'502f8ebd-145a-3fb5-2568-abef04db2625'::uuid,'4557c514-d056-9f18-81de-73bf48425b20'::uuid,'526ed4b7-44ac-a61c-a0a7-f67737906010'::uuid) ORDER BY chat_id, created_at DESC, id DESC
```

LATERAL:
```sql
SELECT latest.*
FROM chats c
CROSS JOIN LATERAL (
    SELECT m.* FROM messages m
    WHERE m.chat_id = c.id
    ORDER BY m.created_at DESC, m.id DESC
    LIMIT 1
) latest
WHERE c.id IN ('f316dbbe-85f1-4587-454d-bfe607cf01de'::uuid,'2a4b6190-231e-8db9-8848-f7b473257e60'::uuid,'c43f2786-f7ea-3a4d-fefc-c60fa991eb95'::uuid,'d27ec30c-da33-a219-4942-deea94d3bbdf'::uuid,'6ad09b69-20d7-5618-b847-f3b2af989faa'::uuid,'ed1ff957-78c9-7371-84ec-15c076827dd6'::uuid,'dcb23fb3-4811-8b8f-3df1-774b7a886b75'::uuid,'8d2dc946-11f2-5ed6-781a-c79aaba65e77'::uuid,'a64b508b-ddec-af19-06fb-249010e6fb6c'::uuid,'9d36ebd5-3ba5-f0df-324c-62a6040df813'::uuid,'7ca75b25-d8a1-013b-1ac5-b9fb4442d927'::uuid,'f9be77bd-b0e0-6958-c7da-b49d71e39fe6'::uuid,'3fffb420-28ad-bc26-6d1c-50f6cb46e172'::uuid,'f9921565-aaa9-8dea-46bd-d5fdf8655acf'::uuid,'0508079d-cea4-9d19-f74f-731888914ffa'::uuid,'1e645194-4089-2a86-2d39-85fdc0b09ef3'::uuid,'b6af08ec-562f-55bc-e26d-60ec81a2b525'::uuid,'502f8ebd-145a-3fb5-2568-abef04db2625'::uuid,'4557c514-d056-9f18-81de-73bf48425b20'::uuid,'526ed4b7-44ac-a61c-a0a7-f67737906010'::uuid)

```

## Было

```text
Index Scan Backward using idx_messages_created_at on messages  (cost=0.47..55374.47 rows=198100 width=162) (actual time=0.024..80.243 rows=200000 loops=1)
  Filter: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
  Rows Removed by Filter: 800000
  Buffers: shared hit=8399 read=21460 written=5816
Planning Time: 0.054 ms
Execution Time: 83.967 ms
```

```text
Index Scan Backward using idx_messages_created_at on messages  (cost=0.47..55374.47 rows=198100 width=162) (actual time=0.013..79.296 rows=200000 loops=1)
  Filter: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
  Rows Removed by Filter: 800000
  Buffers: shared hit=3249 read=26610
Planning Time: 0.064 ms
Execution Time: 83.076 ms
```

```text
Index Scan Backward using idx_messages_created_at on messages  (cost=0.47..55374.47 rows=198100 width=162) (actual time=0.015..79.854 rows=200000 loops=1)
  Filter: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
  Rows Removed by Filter: 800000
  Buffers: shared hit=2793 read=27066
Planning Time: 0.055 ms
Execution Time: 83.580 ms
```

## DISTINCT ON

```text
Unique  (cost=63607.74..64607.58 rows=100 width=162) (actual time=133.141..156.579 rows=20 loops=1)
  Buffers: shared hit=3337 read=21254 written=2, temp read=4282 written=4292
  ->  Sort  (cost=63607.74..64107.66 rows=199967 width=162) (actual time=133.140..151.072 rows=200000 loops=1)
        Sort Key: chat_id, created_at DESC, id DESC
        Sort Method: external merge  Disk: 34256kB
        Buffers: shared hit=3337 read=21254 written=2, temp read=4282 written=4292
        ->  Bitmap Heap Scan on messages  (cost=2206.24..29596.75 rows=199967 width=162) (actual time=6.458..42.676 rows=200000 loops=1)
              Recheck Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
              Heap Blocks: exact=24391
              Buffers: shared hit=3337 read=21254 written=2
              ->  Bitmap Index Scan on idx_messages_chat_id  (cost=0.00..2156.20 rows=199967 width=0) (actual time=4.782..4.782 rows=200000 loops=1)
                    Index Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
                    Buffers: shared hit=40 read=160
Planning Time: 0.061 ms
Execution Time: 157.703 ms
```

```text
Unique  (cost=63607.74..64607.58 rows=100 width=162) (actual time=136.061..159.504 rows=20 loops=1)
  Buffers: shared hit=39 read=24552 written=2, temp read=4282 written=4292
  ->  Sort  (cost=63607.74..64107.66 rows=199967 width=162) (actual time=136.060..153.918 rows=200000 loops=1)
        Sort Key: chat_id, created_at DESC, id DESC
        Sort Method: external merge  Disk: 34256kB
        Buffers: shared hit=39 read=24552 written=2, temp read=4282 written=4292
        ->  Bitmap Heap Scan on messages  (cost=2206.24..29596.75 rows=199967 width=162) (actual time=6.488..45.531 rows=200000 loops=1)
              Recheck Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
              Heap Blocks: exact=24391
              Buffers: shared hit=39 read=24552 written=2
              ->  Bitmap Index Scan on idx_messages_chat_id  (cost=0.00..2156.20 rows=199967 width=0) (actual time=4.812..4.812 rows=200000 loops=1)
                    Index Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
                    Buffers: shared hit=39 read=161
Planning Time: 0.062 ms
Execution Time: 160.594 ms
```

```text
Unique  (cost=63607.74..64607.58 rows=100 width=162) (actual time=131.423..154.568 rows=20 loops=1)
  Buffers: shared hit=40 read=24551, temp read=4282 written=4292
  ->  Sort  (cost=63607.74..64107.66 rows=199967 width=162) (actual time=131.422..149.041 rows=200000 loops=1)
        Sort Key: chat_id, created_at DESC, id DESC
        Sort Method: external merge  Disk: 34256kB
        Buffers: shared hit=40 read=24551, temp read=4282 written=4292
        ->  Bitmap Heap Scan on messages  (cost=2206.24..29596.75 rows=199967 width=162) (actual time=6.460..43.550 rows=200000 loops=1)
              Recheck Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
              Heap Blocks: exact=24391
              Buffers: shared hit=40 read=24551
              ->  Bitmap Index Scan on idx_messages_chat_id  (cost=0.00..2156.20 rows=199967 width=0) (actual time=4.828..4.828 rows=200000 loops=1)
                    Index Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
                    Buffers: shared hit=40 read=160
Planning Time: 0.064 ms
Execution Time: 155.615 ms
```

## LATERAL

```text
Nested Loop  (cost=0.47..95.21 rows=20 width=162) (actual time=0.008..0.039 rows=20 loops=1)
  Buffers: shared hit=82
  ->  Seq Scan on chats c  (cost=0.05..19.20 rows=20 width=16) (actual time=0.003..0.007 rows=20 loops=1)
        Filter: (id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
        Rows Removed by Filter: 80
        Buffers: shared hit=2
  ->  Limit  (cost=0.42..3.79 rows=1 width=162) (actual time=0.001..0.001 rows=1 loops=20)
        Buffers: shared hit=80
        ->  Index Scan using idx_messages_chat_created_id on messages m  (cost=0.42..33655.03 rows=10000 width=162) (actual time=0.001..0.001 rows=1 loops=20)
              Index Cond: (chat_id = c.id)
              Buffers: shared hit=80
Planning Time: 0.040 ms
Execution Time: 0.048 ms
```

```text
Nested Loop  (cost=0.47..95.21 rows=20 width=162) (actual time=0.005..0.032 rows=20 loops=1)
  Buffers: shared hit=82
  ->  Seq Scan on chats c  (cost=0.05..19.20 rows=20 width=16) (actual time=0.002..0.006 rows=20 loops=1)
        Filter: (id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
        Rows Removed by Filter: 80
        Buffers: shared hit=2
  ->  Limit  (cost=0.42..3.79 rows=1 width=162) (actual time=0.001..0.001 rows=1 loops=20)
        Buffers: shared hit=80
        ->  Index Scan using idx_messages_chat_created_id on messages m  (cost=0.42..33655.03 rows=10000 width=162) (actual time=0.001..0.001 rows=1 loops=20)
              Index Cond: (chat_id = c.id)
              Buffers: shared hit=80
Planning Time: 0.028 ms
Execution Time: 0.039 ms
```

```text
Nested Loop  (cost=0.47..95.21 rows=20 width=162) (actual time=0.005..0.031 rows=20 loops=1)
  Buffers: shared hit=82
  ->  Seq Scan on chats c  (cost=0.05..19.20 rows=20 width=16) (actual time=0.002..0.006 rows=20 loops=1)
        Filter: (id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
        Rows Removed by Filter: 80
        Buffers: shared hit=2
  ->  Limit  (cost=0.42..3.79 rows=1 width=162) (actual time=0.001..0.001 rows=1 loops=20)
        Buffers: shared hit=80
        ->  Index Scan using idx_messages_chat_created_id on messages m  (cost=0.42..33655.03 rows=10000 width=162) (actual time=0.001..0.001 rows=1 loops=20)
              Index Cond: (chat_id = c.id)
              Buffers: shared hit=80
Planning Time: 0.024 ms
Execution Time: 0.038 ms
```

