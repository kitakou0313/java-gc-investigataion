package com.example.gc;

import java.util.ArrayList;
import java.util.List;

/**
 * ヒープ領域にある程度の容量を占めるオブジェクトを生成し、GCを実行するクラス。
 *
 * <p>割り当て戦略:</p>
 * <ul>
 *   <li>目標容量の70%: 通常サイズの {@code byte[]} オブジェクト (Eden 領域に割り当て)</li>
 *   <li>目標容量の30%: Humongous オブジェクト (G1HeapRegionSize/2 以上のサイズ。
 *       Eden をスキップして直接 Humongous 領域に割り当てられる)</li>
 * </ul>
 */
public class ObjectGenerator {

    private final long targetBytes;
    private final int normalObjectSizeBytes;
    private final long humongousThresholdBytes;
    private final boolean keepAlive;

    private List<byte[]> normalObjects = new ArrayList<>();
    private List<byte[]> humongousObjects = new ArrayList<>();
    private long humongousAllocatedBytes = 0;

    /**
     * @param targetBytes            割り当て目標サイズ (バイト)
     * @param normalObjectSizeBytes  通常オブジェクトのサイズ (バイト)
     * @param humongousThresholdBytes Humongous 判定閾値 (= G1HeapRegionSize / 2)
     * @param keepAlive              true の場合、GC後もオブジェクト参照を保持する
     */
    public ObjectGenerator(long targetBytes, int normalObjectSizeBytes,
                           long humongousThresholdBytes, boolean keepAlive) {
        this.targetBytes = targetBytes;
        this.normalObjectSizeBytes = normalObjectSizeBytes;
        this.humongousThresholdBytes = humongousThresholdBytes;
        this.keepAlive = keepAlive;
    }

    /**
     * オブジェクトを生成してヒープを埋め、GCを実行する。
     *
     * <p>JIT最適化による dead-code elimination を防ぐため、
     * 各配列の先頭と末尾バイトに非ゼロ値を書き込む。</p>
     *
     * <p>GCは {@code System.gc()} により強制実行される。</p>
     *
     * @throws InterruptedException GC通知待機中に割り込みが発生した場合
     */
    public void generateObjects() throws InterruptedException {
        normalObjects = new ArrayList<>();
        humongousObjects = new ArrayList<>();
        humongousAllocatedBytes = 0;

        long normalTarget = (long) (targetBytes * 0.7);
        long allocated = 0;

        System.out.printf("[generateObjects] 割り当て開始: 目標 %,d MB%n", targetBytes / (1024 * 1024));

        // Phase 1: 通常オブジェクト (Eden 領域に割り当て)
        while (allocated < normalTarget) {
            byte[] obj = new byte[normalObjectSizeBytes];
            // JIT最適化回避: 非ゼロ値を書き込む
            obj[0] = (byte) (allocated & 0xFF);
            obj[normalObjectSizeBytes - 1] = (byte) ((allocated >> 8) & 0xFF);
            normalObjects.add(obj);
            allocated += normalObjectSizeBytes;
        }

        // Phase 2: Humongous オブジェクト (直接 Old/Humongous 領域に割り当て)
        // サイズを閾値の1.5倍にして確実に Humongous 扱いにする
        int humongousSize = (int) (humongousThresholdBytes * 1.5);
        while (allocated < targetBytes) {
            byte[] h = new byte[humongousSize];
            h[0] = (byte) (humongousAllocatedBytes & 0xFF);
            humongousObjects.add(h);
            allocated += humongousSize;
            humongousAllocatedBytes += humongousSize;
        }

        System.out.printf("[generateObjects] 割り当て完了: %,d MB | "
                + "通常オブジェクト %d 個 (%d KB/個) | "
                + "Humongous オブジェクト %d 個 (%d KB/個, 閾値=%d KB)%n",
                allocated / (1024 * 1024),
                normalObjects.size(), normalObjectSizeBytes / 1024,
                humongousObjects.size(), humongousSize / 1024,
                humongousThresholdBytes / 1024);

        if (!keepAlive) {
            // 参照を null にして GC 対象にする
            normalObjects = null;
            humongousObjects = null;
            System.out.println("[generateObjects] オブジェクト参照を解放 (GC対象に設定)");
        } else {
            System.out.println("[generateObjects] オブジェクト参照を保持 (--keep モード)");
        }

        // GCを強制実行
        System.out.println("[generateObjects] GC を実行中...");
        try (GcEventCapture capture = new GcEventCapture()) {
            var notifInfo = capture.triggerAndWait(10_000);
            if (notifInfo != null) {
                System.out.printf("[generateObjects] GC 完了: %s, %d ms%n",
                        notifInfo.getGcName(), notifInfo.getGcInfo().getDuration());
            } else {
                System.out.println("[generateObjects] GC 通知がタイムアウトしました");
            }
        }
    }

    public long getHumongousAllocatedBytes() {
        return humongousAllocatedBytes;
    }
}
