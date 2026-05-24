import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class GcInvestigation {

    public static void main(String[] args) {
        testCase2();
    }

    private static void testCase1(){
        System.gc();

        List<byte[]> objects = generateObjects();

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }
        System.gc();

        objects = null; // 参照を解除してGCでの回収対象にする

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }
        System.gc();
    }

    private static void testCase2(){
        System.gc();

        // objectの生成をブロック内で行うことでブロック終了後にGCの対象になることを確認する
        {
            List<byte[]> objects = generateObjects();
        }

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }
        System.gc();

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }
        System.gc();
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
