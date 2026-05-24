# java-gc-investigataion
JavaのGCについての調査

G1 GC の動作を検証するためのコマンドラインツール。オブジェクト確保の前後で G1 GC 各領域のメモリ使用量を出力し、GC の挙動を観察する。

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

```bash
java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m -Xlog:gc* \
     -jar build/libs/gc-investigation.jar
```

### 推奨 JVM フラグ

| フラグ | 説明 |
|--------|------|
| `-XX:+UseG1GC` | G1 GC を有効化 **(必須)** |
| `-Xmx512m` | 最大ヒープサイズ |
| `-XX:G1HeapRegionSize=1m` | G1 領域サイズを 1MB に固定 |

---

## 出力例

```
=== オブジェクト確保前 ===
  G1 Eden Space         used=       0 KB
  G1 Old Gen            used=   1,242 KB
  G1 Survivor Space     used=       0 KB

オブジェクト確保完了: 100 個 (合計 100 MB)

=== オブジェクト確保後 ===
  G1 Eden Space         used=       0 KB
  G1 Old Gen            used= 206,352 KB
  G1 Survivor Space     used=       0 KB
```

---

## 処理の流れ

```
1. printG1RegionMemoryUsage()  ← System.gc() 後に各領域の使用量を出力
2. generateObjects()           ← byte[] を 1MB × 100 個 (100MB) 確保
3. printG1RegionMemoryUsage()  ← 再度 System.gc() 後に各領域の使用量を出力
```

---

## G1 GC 各領域の説明

### G1 GC のヒープ構造

G1 GC はヒープを等サイズの **領域 (Region)** に分割し、各領域に役割を動的に割り当てる。

```
ヒープ全体
┌─────────────────────────────────────────────────────┐
│  Eden  │ Eden │ Surv │ Old │ Old │ Hum │ Free │ ... │
└─────────────────────────────────────────────────────┘
 ← Young世代 →           ← Old世代 →         ← 空き →
```

JMX で取得できるプールは以下の 3 つ:

| プール名 | 世代 | 役割 |
|---------|------|------|
| `G1 Eden Space` | Young | 新規オブジェクトの割り当て先 |
| `G1 Survivor Space` | Young | Young GC を生き残ったオブジェクトの一時保持場所 |
| `G1 Old Gen` | Old | 長命オブジェクト + Humongous オブジェクト |

### 各領域の特性

**G1 Eden Space**
- 新規オブジェクトはまず Eden に割り当てられる
- Young GC のたびに全領域が回収される → GC 後の使用量は常に 0

**G1 Survivor Space**
- Young GC を生き残ったオブジェクトが移動する
- `TenuringThreshold` 回の GC を生き残ると Old Gen に昇格する

**G1 Old Gen**
- 長命なオブジェクトと Humongous オブジェクトが格納される
- Humongous オブジェクト (サイズ ≥ `G1HeapRegionSize / 2`) は Eden をスキップして直接ここに割り当てられる
- JMX では Humongous 領域も Old Gen の一部として報告される (独立したプールなし)

### メモリ使用量の取得方法

```java
// MemoryPoolMXBean でヒーププールを列挙
for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
    if (pool.getType() == MemoryType.HEAP) {
        MemoryUsage u = pool.getUsage();
        System.out.printf("%s: used=%d KB%n", pool.getName(), u.getUsed() / 1024);
    }
}
```

---

## プロジェクト構成

```
java-gc-investigataion/
├── build.gradle
├── settings.gradle
└── src/main/java/
    └── GcInvestigation.java   # main / printG1RegionMemoryUsage / generateObjects
```
