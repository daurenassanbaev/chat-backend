# getChats: SQL experiment

Run: `./gradlew :message-service:queryBenchmark --rerun-tasks`

PostgreSQL 16 Alpine in Testcontainers, default settings. 1,000,000 messages,
100 chats, 20 selected chats, payload 100 characters, evenly distributed history.
Before: original single-column indexes; query returns 200,000 history rows.
After: added (chat_id, created_at DESC, id DESC); query returns 20 latest messages.
One warm-up plus three EXPLAIN (ANALYZE, BUFFERS) runs per query, same container.
This measures SQL only, excluding network transfer and Java entity allocation.
Local timings are not a production SLA; the dataset is synthetic.

Before SQL:
```sql
SELECT * FROM messages WHERE chat_id IN ('f316dbbe-85f1-4587-454d-bfe607cf01de'::uuid,'2a4b6190-231e-8db9-8848-f7b473257e60'::uuid,'c43f2786-f7ea-3a4d-fefc-c60fa991eb95'::uuid,'d27ec30c-da33-a219-4942-deea94d3bbdf'::uuid,'6ad09b69-20d7-5618-b847-f3b2af989faa'::uuid,'ed1ff957-78c9-7371-84ec-15c076827dd6'::uuid,'dcb23fb3-4811-8b8f-3df1-774b7a886b75'::uuid,'8d2dc946-11f2-5ed6-781a-c79aaba65e77'::uuid,'a64b508b-ddec-af19-06fb-249010e6fb6c'::uuid,'9d36ebd5-3ba5-f0df-324c-62a6040df813'::uuid,'7ca75b25-d8a1-013b-1ac5-b9fb4442d927'::uuid,'f9be77bd-b0e0-6958-c7da-b49d71e39fe6'::uuid,'3fffb420-28ad-bc26-6d1c-50f6cb46e172'::uuid,'f9921565-aaa9-8dea-46bd-d5fdf8655acf'::uuid,'0508079d-cea4-9d19-f74f-731888914ffa'::uuid,'1e645194-4089-2a86-2d39-85fdc0b09ef3'::uuid,'b6af08ec-562f-55bc-e26d-60ec81a2b525'::uuid,'502f8ebd-145a-3fb5-2568-abef04db2625'::uuid,'4557c514-d056-9f18-81de-73bf48425b20'::uuid,'526ed4b7-44ac-a61c-a0a7-f67737906010'::uuid) ORDER BY created_at DESC
```

After SQL:
```sql
SELECT DISTINCT ON (chat_id) * FROM messages WHERE chat_id IN ('f316dbbe-85f1-4587-454d-bfe607cf01de'::uuid,'2a4b6190-231e-8db9-8848-f7b473257e60'::uuid,'c43f2786-f7ea-3a4d-fefc-c60fa991eb95'::uuid,'d27ec30c-da33-a219-4942-deea94d3bbdf'::uuid,'6ad09b69-20d7-5618-b847-f3b2af989faa'::uuid,'ed1ff957-78c9-7371-84ec-15c076827dd6'::uuid,'dcb23fb3-4811-8b8f-3df1-774b7a886b75'::uuid,'8d2dc946-11f2-5ed6-781a-c79aaba65e77'::uuid,'a64b508b-ddec-af19-06fb-249010e6fb6c'::uuid,'9d36ebd5-3ba5-f0df-324c-62a6040df813'::uuid,'7ca75b25-d8a1-013b-1ac5-b9fb4442d927'::uuid,'f9be77bd-b0e0-6958-c7da-b49d71e39fe6'::uuid,'3fffb420-28ad-bc26-6d1c-50f6cb46e172'::uuid,'f9921565-aaa9-8dea-46bd-d5fdf8655acf'::uuid,'0508079d-cea4-9d19-f74f-731888914ffa'::uuid,'1e645194-4089-2a86-2d39-85fdc0b09ef3'::uuid,'b6af08ec-562f-55bc-e26d-60ec81a2b525'::uuid,'502f8ebd-145a-3fb5-2568-abef04db2625'::uuid,'4557c514-d056-9f18-81de-73bf48425b20'::uuid,'526ed4b7-44ac-a61c-a0a7-f67737906010'::uuid) ORDER BY chat_id, created_at DESC, id DESC
```

## Before

```text
Index Scan Backward using idx_messages_created_at on messages  (cost=0.47..55374.47 rows=200833 width=162) (actual time=0.016..78.225 rows=200000 loops=1)
  Filter: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
  Rows Removed by Filter: 800000
  Buffers: shared hit=8399 read=21460 written=5717
Planning Time: 0.045 ms
Execution Time: 82.021 ms
```

```text
Index Scan Backward using idx_messages_created_at on messages  (cost=0.47..55374.47 rows=200833 width=162) (actual time=0.016..77.720 rows=200000 loops=1)
  Filter: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
  Rows Removed by Filter: 800000
  Buffers: shared hit=3249 read=26610
Planning Time: 0.055 ms
Execution Time: 81.553 ms
```

```text
Index Scan Backward using idx_messages_created_at on messages  (cost=0.47..55374.47 rows=200833 width=162) (actual time=0.015..79.029 rows=200000 loops=1)
  Filter: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
  Rows Removed by Filter: 800000
  Buffers: shared hit=2794 read=27065
Planning Time: 0.055 ms
Execution Time: 82.840 ms
```

## After

```text
Unique  (cost=63701.35..64703.51 rows=100 width=162) (actual time=131.871..155.542 rows=20 loops=1)
  Buffers: shared hit=3352 read=21239 written=5, temp read=4282 written=4292
  ->  Sort  (cost=63701.35..64202.43 rows=200433 width=162) (actual time=131.870..149.932 rows=200000 loops=1)
        Sort Key: chat_id, created_at DESC, id DESC
        Sort Method: external merge  Disk: 34256kB
        Buffers: shared hit=3352 read=21239 written=5, temp read=4282 written=4292
        ->  Bitmap Heap Scan on messages  (cost=2209.96..29607.45 rows=200433 width=162) (actual time=6.447..42.072 rows=200000 loops=1)
              Recheck Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
              Heap Blocks: exact=24391
              Buffers: shared hit=3352 read=21239 written=5
              ->  Bitmap Index Scan on idx_messages_chat_id  (cost=0.00..2159.80 rows=200433 width=0) (actual time=4.767..4.767 rows=200000 loops=1)
                    Index Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
                    Buffers: shared hit=40 read=160
Planning Time: 0.055 ms
Execution Time: 156.631 ms
```

```text
Unique  (cost=63701.35..64703.51 rows=100 width=162) (actual time=135.033..159.193 rows=20 loops=1)
  Buffers: shared hit=39 read=24552 written=2, temp read=4282 written=4292
  ->  Sort  (cost=63701.35..64202.43 rows=200433 width=162) (actual time=135.032..153.570 rows=200000 loops=1)
        Sort Key: chat_id, created_at DESC, id DESC
        Sort Method: external merge  Disk: 34256kB
        Buffers: shared hit=39 read=24552 written=2, temp read=4282 written=4292
        ->  Bitmap Heap Scan on messages  (cost=2209.96..29607.45 rows=200433 width=162) (actual time=6.437..44.704 rows=200000 loops=1)
              Recheck Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
              Heap Blocks: exact=24391
              Buffers: shared hit=39 read=24552 written=2
              ->  Bitmap Index Scan on idx_messages_chat_id  (cost=0.00..2159.80 rows=200433 width=0) (actual time=4.769..4.769 rows=200000 loops=1)
                    Index Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
                    Buffers: shared hit=39 read=161
Planning Time: 0.060 ms
Execution Time: 160.423 ms
```

```text
Unique  (cost=63701.35..64703.51 rows=100 width=162) (actual time=135.550..159.038 rows=20 loops=1)
  Buffers: shared hit=40 read=24551, temp read=4282 written=4292
  ->  Sort  (cost=63701.35..64202.43 rows=200433 width=162) (actual time=135.549..153.482 rows=200000 loops=1)
        Sort Key: chat_id, created_at DESC, id DESC
        Sort Method: external merge  Disk: 34256kB
        Buffers: shared hit=40 read=24551, temp read=4282 written=4292
        ->  Bitmap Heap Scan on messages  (cost=2209.96..29607.45 rows=200433 width=162) (actual time=6.680..45.851 rows=200000 loops=1)
              Recheck Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
              Heap Blocks: exact=24391
              Buffers: shared hit=40 read=24551
              ->  Bitmap Index Scan on idx_messages_chat_id  (cost=0.00..2159.80 rows=200433 width=0) (actual time=5.006..5.006 rows=200000 loops=1)
                    Index Cond: (chat_id = ANY ('{f316dbbe-85f1-4587-454d-bfe607cf01de,2a4b6190-231e-8db9-8848-f7b473257e60,c43f2786-f7ea-3a4d-fefc-c60fa991eb95,d27ec30c-da33-a219-4942-deea94d3bbdf,6ad09b69-20d7-5618-b847-f3b2af989faa,ed1ff957-78c9-7371-84ec-15c076827dd6,dcb23fb3-4811-8b8f-3df1-774b7a886b75,8d2dc946-11f2-5ed6-781a-c79aaba65e77,a64b508b-ddec-af19-06fb-249010e6fb6c,9d36ebd5-3ba5-f0df-324c-62a6040df813,7ca75b25-d8a1-013b-1ac5-b9fb4442d927,f9be77bd-b0e0-6958-c7da-b49d71e39fe6,3fffb420-28ad-bc26-6d1c-50f6cb46e172,f9921565-aaa9-8dea-46bd-d5fdf8655acf,0508079d-cea4-9d19-f74f-731888914ffa,1e645194-4089-2a86-2d39-85fdc0b09ef3,b6af08ec-562f-55bc-e26d-60ec81a2b525,502f8ebd-145a-3fb5-2568-abef04db2625,4557c514-d056-9f18-81de-73bf48425b20,526ed4b7-44ac-a61c-a0a7-f67737906010}'::uuid[]))
                    Buffers: shared hit=40 read=160
Planning Time: 0.068 ms
Execution Time: 160.118 ms
```

