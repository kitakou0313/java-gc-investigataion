package com.example.gc;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G1 GC の各領域のメモリ使用量を stdout に出力するクラス。
 *
 * <p>JMXで取得可能な G1 GC のヒーププール:</p>
 * <ul>
 *   <li>G1 Eden Space  - Young世代のEden領域</li>
 *   <li>G1 Survivor Space - Young世代のSurvivor領域</li>
 *   <li>G1 Old Gen - Old世代 (Humongousオブジェクトも含む)</li>
 * </ul>
 *
 * <p>注意: Humongous領域はJMXでは独立したプールとして公開されておらず、
 * G1 Old Gen の一部として扱われる。Humongous閾値は G1HeapRegionSize/2 で計算する。</p>
 */
public class G1RegionReporter {

    // G1 GC のヒーププール名 (出力順)
    private static final String[] HEAP_POOL_ORDER = {
        "G1 Eden Space",
        "G1 Survivor Space",
        "G1 Old Gen"
    };

    private static final long GC_TIMEOUT_MS = 10_000;

    private final long g1RegionSizeBytes;
    private final long humongousThresholdBytes;
    private final boolean verbose;

    public G1RegionReporter(boolean verbose) {
        HotSpotDiagnosticMXBean diag =
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        this.g1RegionSizeBytes =
            Long.parseLong(diag.getVMOption("G1HeapRegionSize").getValue());
        this.humongousThresholdBytes = g1RegionSizeBytes / 2;
        this.verbose = verbose;
    }

    public long getHumongousThresholdBytes() {
        return humongousThresholdBytes;
    }

    /**
     * GCを実行し、G1 GCの各領域のメモリ使用量を stdout に出力する。
     *
     * @throws InterruptedException GC通知待機中に割り込みが発生した場合
     */
    public void printG1RegionMemoryUsage() throws InterruptedException {
        printG1RegionMemoryUsage(0);
    }

    /**
     * GCを実行し、G1 GCの各領域のメモリ使用量を stdout に出力する。
     *
     * @param estimatedHumongousBytes 事前に割り当てた Humongous オブジェクトの概算バイト数
     * @throws InterruptedException GC通知待機中に割り込みが発生した場合
     */
    public void printG1RegionMemoryUsage(long estimatedHumongousBytes) throws InterruptedException {
        System.out.println("[printG1RegionMemoryUsage] GC を実行中...");

        GarbageCollectionNotificationInfo notifInfo;
        try (GcEventCapture capture = new GcEventCapture()) {
            notifInfo = capture.triggerAndWait(GC_TIMEOUT_MS);
        }

        // GC後のライブプール状態を取得
        Map<String, MemoryUsage> liveState = new LinkedHashMap<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                liveState.put(pool.getName(), pool.getUsage());
            }
        }

        GcInfo gcInfo = notifInfo != null ? notifInfo.getGcInfo() : null;

        printReport(notifInfo, gcInfo, liveState, estimatedHumongousBytes);
    }

    private void printReport(GarbageCollectionNotificationInfo notifInfo,
                             GcInfo gcInfo,
                             Map<String, MemoryUsage> liveState,
                             long estimatedHumongousBytes) {
        System.out.println();
        System.out.println("=== G1 GC 領域メモリ使用量レポート ===");
        System.out.printf("タイムスタンプ     : %s%n", new Date());
        System.out.printf("G1 領域サイズ      : %,d KB%n", g1RegionSizeBytes / 1024);

        if (notifInfo != null && gcInfo != null) {
            System.out.printf("GC コレクター      : %s (GC #%d, %d ms)%n",
                notifInfo.getGcName(), gcInfo.getId(), gcInfo.getDuration());
            System.out.printf("GC アクション      : %s%n", notifInfo.getGcAction());
            System.out.printf("GC 原因            : %s%n", notifInfo.getGcCause());
        } else {
            System.out.println("GC 情報            : タイムアウト (通知なし)");
        }

        // --- ライブプール状態 (GC後) ---
        System.out.println();
        System.out.println("[GC後のライブプール状態]");
        printTableHeader("プール名", "使用量 (KB)", "Committed (KB)", "最大 (KB)");
        for (String poolName : HEAP_POOL_ORDER) {
            MemoryUsage u = liveState.get(poolName);
            if (u != null) {
                printPoolRow(poolName, u);
            }
        }

        // --- GC前後の差分 ---
        if (gcInfo != null && !gcInfo.getMemoryUsageBeforeGc().isEmpty()) {
            System.out.println();
            System.out.printf("[GC前後の比較 (GC #%d)]%n", gcInfo.getId());
            System.out.printf("%-26s %14s %14s %14s%n",
                "プール名", "GC前 (KB)", "GC後 (KB)", "差分 (KB)");
            System.out.printf("%-26s %14s %14s %14s%n",
                "-".repeat(26), "-".repeat(14), "-".repeat(14), "-".repeat(14));

            Map<String, MemoryUsage> beforeGc = gcInfo.getMemoryUsageBeforeGc();
            Map<String, MemoryUsage> afterGc = gcInfo.getMemoryUsageAfterGc();

            for (String poolName : HEAP_POOL_ORDER) {
                MemoryUsage before = beforeGc.get(poolName);
                MemoryUsage after = afterGc.get(poolName);
                if (before != null && after != null) {
                    long deltaKb = (after.getUsed() - before.getUsed()) / 1024;
                    System.out.printf("%-26s %,14d %,14d %+,14d%n",
                        poolName,
                        before.getUsed() / 1024,
                        after.getUsed() / 1024,
                        deltaKb);
                }
            }
        }

        // --- 詳細モード: 各コレクターの情報 ---
        if (verbose) {
            printVerboseCollectorBreakdown();
        }

        // --- Humongous 領域の注記 ---
        System.out.println();
        System.out.println("[Humongous 領域情報]");
        System.out.printf("Humongous 閾値     : オブジェクトサイズ >= %,d KB で Humongous 領域に割り当て%n",
            humongousThresholdBytes / 1024);
        System.out.printf("G1 領域サイズ      : %,d KB → Humongous オブジェクトは ceil(サイズ/領域サイズ) 個の連続領域を占有%n",
            g1RegionSizeBytes / 1024);
        if (estimatedHumongousBytes > 0) {
            System.out.printf("今回の割り当て推定 : 約 %,d KB の Humongous オブジェクト%n",
                estimatedHumongousBytes / 1024);
        }
        System.out.println("注記: JMX では Humongous 領域は 'G1 Old Gen' の一部として報告される (独立したプールなし)。");
        System.out.println("      詳細な Humongous 領域カウントには -Xlog:gc+heap:stdout または JFR を使用。");
        System.out.println();
    }

    private void printVerboseCollectorBreakdown() {
        System.out.println();
        System.out.println("[詳細: コレクター別の最終GC情報]");
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            if (gcBean instanceof com.sun.management.GarbageCollectorMXBean sunGcBean) {
                GcInfo info = sunGcBean.getLastGcInfo();
                if (info == null) {
                    System.out.printf("  %-32s : まだGCが実行されていません%n", gcBean.getName());
                } else {
                    System.out.printf("  %-32s : GC #%d, %d ms%n",
                        gcBean.getName(), info.getId(), info.getDuration());
                }
            }
        }
    }

    private void printTableHeader(String col1, String col2, String col3, String col4) {
        System.out.printf("%-26s %14s %14s %12s%n", col1, col2, col3, col4);
        System.out.printf("%-26s %14s %14s %12s%n",
            "-".repeat(26), "-".repeat(14), "-".repeat(14), "-".repeat(12));
    }

    private void printPoolRow(String name, MemoryUsage u) {
        String maxStr = u.getMax() == -1 ? "動的" : String.format("%,d", u.getMax() / 1024);
        System.out.printf("%-26s %,14d %,14d %12s%n",
            name,
            u.getUsed() / 1024,
            u.getCommitted() / 1024,
            maxStr);
    }
}
