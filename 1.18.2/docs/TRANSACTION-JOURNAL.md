# Transaction Journal

## なぜ必要か

Worldをcheckpointへ戻しても、外部のInventory/XP/Ender Chest同期やMySQLが同じ時点へ戻るとは限りません。Chest側だけrollbackしてPlayer側が新しいままだとduplication、逆ならlossが起き得ます。AntiCrashは外部DBを推測して書き換えず、境界を可視化します。

## Modes

```toml
[consistency]
mode = "IGNORE" # IGNORE / WARN / JOURNAL
```

- `IGNORE`: 何も追跡せず、通常のWorld rollbackと同じ外部整合性リスクを受け入れます。
- `WARN`: Container、Block Place/Break、Item Use、Entity、Drop等のmutationをカウントし、Recovery後にもUNRESOLVED warningを出せるよう、checkpoint境界・transaction ID・typeだけの非機密summaryをdurable JSONLへappendします。
- `JOURNAL`: WARNの境界情報に加え、Player/Item/NBT等を含む詳細recordを`.anticrash/journal/transactions-<run-id>.jsonl`へappendします。

## Record schema

各行は`formatVersion=1`を持ち、次の項目を記録します。

```json
{
  "formatVersion": 1,
  "transactionId": "uuid",
  "checkpointId": "cp-000123",
  "fallbackCheckpointId": "cp-000122",
  "serverTick": 123456,
  "wallClockEpochMs": 1780000000000,
  "playerUuid": "uuid",
  "transactionType": "BLOCK_PLACE",
  "source": "player",
  "destination": "world",
  "dimension": "minecraft:overworld",
  "x": 0,
  "y": 64,
  "z": 0,
  "itemStackId": "minecraft:stone",
  "count": 1,
  "nbt": "base64-nbt-or-null",
  "before": "base64-nbt-or-UNAVAILABLE",
  "after": "UNAVAILABLE",
  "reconciliationStatus": "UNRESOLVED"
}
```

checkpoint commit中のtransactionは、候補`checkpointId`と、commit失敗時にも有効な`fallbackCheckpointId`の両方へ保守的に関連付けます。増分元がまだ検証できない期間は`*`を使い、どの復旧候補でも未解決として扱います。

`before`/`after`を推測で埋めません。実際に取得できない状態は`UNAVAILABLE`、照合状態は`UNRESOLVED`のままです。1行はUTF-8で1 MiB以下に制限し、NBTで超過する場合はtransaction境界を残して`nbt`/`before`/`after`を`OMITTED_TOO_LARGE`へ置換し、`detailsOmitted=true`を付けます。読込み時も1 MiB超の行を割り当て続けずmalformed/UNRESOLVEDとして扱います。Journalは自動reconcileを行わないため、利用者が外部同期Modと照合してください。

## ExternalPlayerDataBridge

`ExternalPlayerDataBridge`は特定DB schemaを知らないSPIです。

- `onCheckpoint(checkpointId)`: 新しいcheckpointがcommitされた通知
- `onTransaction(record)`: WARN/JOURNAL mutation通知
- `onRecovery(checkpointId)`: recovery開始時の照合通知

Bridge例は外部側でUUIDとtransaction IDを記録し、s1のWorld checkpoint時刻とs2/MySQL側の更新時刻を比較します。AntiCrashはs2、hub、c1、c2、Velocity、MySQLをrollbackしません。

## Commands and files

```text
/ac journal status
/ac journal list
.anticrash/journal/transactions-<run-id>.jsonl
```

ファイルはrun単位でappendされ、各recordのflushとchannel forceを完了してから処理を返します。Recovery後も既存行を削除せず、`reconciliationStatus=UNRESOLVED`を運用上の照合対象にしてください。WARN recordは意図的にPlayer UUID、Item、NBT、座標等を持ちません。
