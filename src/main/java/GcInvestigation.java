import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class GcInvestigation {

    public static void main(String[] args) {
        System.gc();

        // System.out.println("=== オブジェクト確保前 ===");
        // printG1RegionMemoryUsage();

        List<byte[]> objects = generateObjects();

        // System.out.println("\n=== オブジェクト確保後 ===");
        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }
        System.gc();
        // printG1RegionMemoryUsage();

        objects = null; // 参照を解除してGCでの回収対象にする

        // System.out.println("\n=== 参照の解除後 ===");
        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }
        System.gc();
        // printG1RegionMemoryUsage();
    }

    /** 各 G1 GC 領域のメモリ使用量を stdout に出力し、GC を実行する */
    static void printG1RegionMemoryUsage() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage u = pool.getUsage();
                System.out.printf("  %-20s  used=%,8d KB%n",
                        pool.getName(), u.getUsed() / 1024);
            }
        }
    }

    /** ヒープ領域に 100MB 分のオブジェクトを確保する */
    static List<byte[]> generateObjects() {
        List<byte[]> objects = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            objects.add(new byte[1024 * 1024]); // 1MB × 100 = 100MB
        }
        // System.out.printf("\nオブジェクト確保完了: %d 個 (合計 %d MB)%n",objects.size(), objects.size());
        return objects; // 参照を返すことで GC されないようにする
    }
}
