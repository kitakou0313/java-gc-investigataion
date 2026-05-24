# java-gc-investigataion
JavaのGCについての調査

G1 GC の動作を検証するためのコマンドラインツール。ヒープへのオブジェクト割り当てと、G1 GC 各領域のメモリ使用量レポートを通じて GC の挙動を観察できる。

---

## 前提条件

| 項目 | バージョン |
|------|-----------|
| Java | 21 以上 |
| Gradle | Wrapper (`./gradlew`) 経由で自動取得 — 手動インストール不要 |

---

## ビルド

```bash
./gradlew jar
# → build/libs/gc-investigation.jar が生成される
```

---

## 実行方法

### 基本形式

```bash
java [JVMフラグ] -jar build/libs/gc-investigation.jar <command> [options]
```

### 推奨 JVM フラグ

| フラグ | 説明 |
|--------|------|
| `-XX:+UseG1GC` | G1 GC を有効化 **(必須)** |
| `-Xmx512m` | 最大ヒープサイズ |
| `-Xms128m` | 初期ヒープサイズ (安定した挙動のため推奨) |
| `-XX:G1HeapRegionSize=1m` | G1 領域サイズを 1MB に固定 → Humongous 閾値 = 512KB |
| `-Xlog:gc:stderr:time,uptime,level,tags` | GC ログを stderr に出力 (stdout をクリーンに保つ) |

> **注意**: `-XX:+ExplicitGCInvokesConcurrent` と `-XX:+DisableExplicitGC` はツールの動作を壊すため使用しないこと。

### コマンド一覧

| コマンド | 説明 |
|----------|------|
| `generate` | オブジェクトを生成し GC 実行後、G1 領域の使用量を出力 |
| `regions` | GC を実行し、G1 領域の使用量のみを出力 (オブジェクト生成なし) |
| `both` | `generate` の別名 |

### オプション一覧

| オプション | デフォルト | 説明 |
|-----------|-----------|------|
| `--size <MB>` | 100 | 割り当て目標サイズ (MB) |
| `--object-size <KB>` | 4 | 通常オブジェクト 1 個あたりのサイズ (KB) |
| `--keep` | false | GC 後もオブジェクト参照を保持する (解放せずに Old Gen に残す観察用) |
| `--repeat <N>` | 1 | 割り当て→GC→レポートのサイクルを N 回繰り返す |
| `--verbose` | false | コレクター別の詳細 GC 情報も表示する |
| `--help`, `-h` | — | ヘルプを表示 |

### 実行例

```bash
# 100MB 割り当て後に G1 領域使用量を出力
java -XX:+UseG1GC -Xmx512m -Xms128m -XX:G1HeapRegionSize=1m \
     -Xlog:gc:stderr:time,uptime,level,tags \
     -jar build/libs/gc-investigation.jar generate --size 100

# G1 領域のみ確認 (オブジェクト生成なし)
java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m \
     -jar build/libs/gc-investigation.jar regions

# オブジェクトを保持したまま GC (Old Gen に残ることを観察)
java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m \
     -jar build/libs/gc-investigation.jar generate --size 100 --keep --verbose

# 3 回繰り返して GC の挙動を観察
java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m \
     -jar build/libs/gc-investigation.jar both --size 50 --repeat 3
```

---

## 出力例

```
[generateObjects] 割り当て開始: 目標 100 MB
[generateObjects] 割り当て完了: 100 MB | 通常オブジェクト 17920 個 (4 KB/個) | Humongous オブジェクト 40 個 (768 KB/個, 閾値=512 KB)
[generateObjects] オブジェクト参照を解放 (GC対象に設定)
[generateObjects] GC を実行中...
[generateObjects] GC 完了: G1 Old Generation, 2 ms

[printG1RegionMemoryUsage] GC を実行中...

=== G1 GC 領域メモリ使用量レポート ===
タイムスタンプ     : Sun May 24 16:51:21 JST 2026
G1 領域サイズ      : 1,024 KB
GC コレクター      : G1 Old Generation (GC #2, 2 ms)
GC アクション      : end of major GC
GC 原因            : System.gc()

[GC後のライブプール状態]
プール名                             使用量 (KB) Committed (KB)      最大 (KB)
-------------------------- -------------- -------------- ------------
G1 Eden Space                           0         41,984           動的
G1 Survivor Space                       0              0           動的
G1 Old Gen                          1,639         89,088      524,288

[GC前後の比較 (GC #2)]
プール名                             GC前 (KB)       GC後 (KB)        差分 (KB)
-------------------------- -------------- -------------- --------------
G1 Eden Space                           0              0             +0
G1 Survivor Space                       0              0             +0
G1 Old Gen                          1,556          1,639            +82

[Humongous 領域情報]
Humongous 閾値     : オブジェクトサイズ >= 512 KB で Humongous 領域に割り当て
G1 領域サイズ      : 1,024 KB → Humongous オブジェクトは ceil(サイズ/領域サイズ) 個の連続領域を占有
今回の割り当て推定 : 約 30,720 KB の Humongous オブジェクト
```

---

## G1 GC 各領域の説明と算出ロジック

### G1 GC のヒープ構造

G1 GC はヒープを等サイズの **領域 (Region)** に分割し、各領域に役割 (Eden / Survivor / Old / Humongous / Free) を動的に割り当てる。

```
ヒープ全体 (例: -Xmx512m)
┌────────────────────────────────────────────────────────────────────┐
│  Eden  │ Eden │ Surv │ Old │ Old │ Hum │ Hum │ Free │ Free │ ... │
│  (1MB) │(1MB) │(1MB) │(1MB)│(1MB)│(2MB)│(連続)│      │      │     │
└────────────────────────────────────────────────────────────────────┘
 ← Young世代 →               ← Old世代 →                ← 空き →
```

JMX で取得できるプールは以下の 3 つ:

| プール名 | 世代 | 役割 |
|---------|------|------|
| `G1 Eden Space` | Young | 新規オブジェクトの割り当て先 |
| `G1 Survivor Space` | Young | Young GC を生き残ったオブジェクトの一時保持場所 |
| `G1 Old Gen` | Old | 長命オブジェクト + Humongous オブジェクト |

---

### G1 Eden Space

**役割**: すべての新規オブジェクトはまず Eden に割り当てられる。

**算出 API**:
```java
ManagementFactory.getMemoryPoolMXBeans().stream()
    .filter(p -> "G1 Eden Space".equals(p.getName()))
    .findFirst()
    .map(MemoryPoolMXBean::getUsage);
```

**特性**:
- Young GC (Evacuation Pause) のたびに **全領域が回収** される → GC 後の使用量は常に 0
- Max サイズは `動的` — G1 が Pause Target に合わせて Eden 領域数を自動調整する
- 本ツールでは `ObjectGenerator` Phase 1 が生成する通常サイズ (`--object-size` KB) の `byte[]` がここに積まれる

---

### G1 Survivor Space

**役割**: Young GC を 1 回以上生き残ったオブジェクトが移動する領域。

**算出 API**:
```java
ManagementFactory.getMemoryPoolMXBeans().stream()
    .filter(p -> "G1 Survivor Space".equals(p.getName()))
    .findFirst()
    .map(MemoryPoolMXBean::getUsage);
```

**特性**:
- `TenuringThreshold` 回 (デフォルト最大 15 回) の GC を生き残ると **Old Gen に昇格 (Promote)**
- Max サイズは `動的` — G1 が Survivor 領域数を自動調整する
- Full GC (`System.gc()`) 後は Survivor に残存オブジェクトがないため使用量は 0 になることが多い

---

### G1 Old Gen

**役割**: 長命なオブジェクトと Humongous オブジェクトが格納される。

**算出 API**:
```java
ManagementFactory.getMemoryPoolMXBeans().stream()
    .filter(p -> "G1 Old Gen".equals(p.getName()))
    .findFirst()
    .map(MemoryPoolMXBean::getUsage);
```

**特性**:
- `--keep` オプション付きで実行すると、生成したオブジェクトが GC 後も Old Gen に残るため使用量が大きくなる
- JMX では **Humongous 領域も Old Gen の一部** として報告される (独立したプールなし)
- Max サイズ = `-Xmx` — ヒープ全体を Old Gen として使える

---

### Humongous 領域 (Old Gen の内訳)

**役割**: 単一オブジェクトのサイズが `G1HeapRegionSize / 2` 以上の場合に割り当てられる専用領域。

**閾値の算出**:
```java
// HotSpotDiagnosticMXBean から実行時に G1HeapRegionSize を読み取る
HotSpotDiagnosticMXBean diag =
    ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
long regionSizeBytes = Long.parseLong(diag.getVMOption("G1HeapRegionSize").getValue());
long humongousThreshold = regionSizeBytes / 2;  // この値以上が Humongous 扱い
```

| `-XX:G1HeapRegionSize` | Humongous 閾値 |
|------------------------|---------------|
| 1 MB | 512 KB |
| 2 MB | 1 MB |
| 4 MB | 2 MB |

**割り当ての特性**:
- Eden をスキップして **直接 Old 世代の連続した領域** に配置される
- 必要領域数 = `⌈オブジェクトサイズ / G1HeapRegionSize⌉` 個の連続領域を占有
- 本ツールでは `ObjectGenerator` Phase 2 が `閾値 × 1.5` サイズの `byte[]` を生成して Humongous 割り当てを発生させる

**JMX の制約**:
標準 JMX API では Humongous 領域を独立したプールとして取得できない。詳細な Humongous 領域カウントが必要な場合は以下を使用する:
```bash
# Humongous 割り当てイベントをログ出力
java -Xlog:gc+humongous:stdout ...

# ヒープ領域の詳細をログ出力
java -Xlog:gc+heap:stdout ...
```

---

## GC 前後の差分の算出ロジック

`GcInfo` オブジェクトからプールごとの GC 前後メモリ使用量を取得する:

```java
// GarbageCollectionNotificationInfo から GcInfo を取得
GcInfo gcInfo = notifInfo.getGcInfo();

// GC 前後のメモリ使用量 (Map<プール名, MemoryUsage>)
Map<String, MemoryUsage> before = gcInfo.getMemoryUsageBeforeGc();
Map<String, MemoryUsage> after  = gcInfo.getMemoryUsageAfterGc();

// 差分の計算 (KB 単位)
long deltaKb = (after.get("G1 Old Gen").getUsed()
              - before.get("G1 Old Gen").getUsed()) / 1024;
```

`com.sun.management.GcInfo` は JDK 内部 API (`jdk.management` モジュール) だが、`--add-modules` なしで利用できる。

---

## GC 実行の仕組み

本ツールは以下の手順で GC を実行し、完了を確認する:

```
1. GcEventCapture が全 GC MXBean に NotificationListener を登録
2. System.gc() を呼び出す → G1 Full GC (Major GC) が起動
3. CountDownLatch で GC 完了通知を待機 (タイムアウト: 10 秒)
4. GarbageCollectionNotificationInfo#getGcCause() == "System.gc()" の
   通知のみを捕捉 → latch.countDown()
5. GcInfo から before/after メモリ情報を取得
6. MemoryPoolMXBean から GC 後のライブプール状態を取得して出力
```

> **注意**: `-XX:+ExplicitGCInvokesConcurrent` を付けると `System.gc()` が
> Young GC になり cause 文字列が変わるため、GC 通知の捕捉に失敗する。

---

## プロジェクト構成

```
java-gc-investigataion/
├── build.gradle                              # Gradle ビルド設定 (fat JAR 生成)
├── settings.gradle
└── src/main/java/com/example/gc/
    ├── GcInvestigation.java                  # main() + CLI ディスパッチ
    ├── CliArgs.java                          # コマンドライン引数パース
    ├── ObjectGenerator.java                  # generateObjects() の実装
    ├── G1RegionReporter.java                 # printG1RegionMemoryUsage() の実装
    └── GcEventCapture.java                   # GC通知リスナー + CountDownLatch
```
