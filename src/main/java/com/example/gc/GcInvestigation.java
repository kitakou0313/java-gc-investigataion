package com.example.gc;

/**
 * G1 GC 検証ツール - エントリポイント。
 *
 * <p>使用方法:</p>
 * <pre>
 * java -XX:+UseG1GC -Xmx512m -Xms128m -XX:G1HeapRegionSize=1m \
 *      -jar gc-investigation.jar &lt;command&gt; [options]
 *
 * Commands:
 *   generate   オブジェクト生成後にG1領域使用量を出力
 *   regions    G1領域使用量のみ出力 (GCを実行)
 *   both       generate と同じ (別名)
 *
 * Options:
 *   --size &lt;MB&gt;         割り当て目標サイズ (default: 100)
 *   --object-size &lt;KB&gt;  通常オブジェクトサイズ (default: 4)
 *   --keep              GC後もオブジェクトを保持
 *   --repeat &lt;N&gt;        サイクルの繰り返し回数 (default: 1)
 *   --verbose           コレクター別の詳細GC情報も表示
 *   --help              ヘルプを表示
 * </pre>
 */
public class GcInvestigation {

    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);

        if (cli.showHelp()) {
            printHelp();
            return;
        }

        G1RegionReporter reporter = new G1RegionReporter(cli.verbose());

        switch (cli.command()) {
            case "generate", "both" -> {
                ObjectGenerator gen = new ObjectGenerator(
                    (long) cli.sizeMb() * 1024 * 1024,
                    cli.objectSizeKb() * 1024,
                    reporter.getHumongousThresholdBytes(),
                    cli.keepAlive()
                );
                for (int i = 0; i < cli.repeat(); i++) {
                    if (cli.repeat() > 1) {
                        System.out.printf("%n--- イテレーション %d / %d ---%n", i + 1, cli.repeat());
                    }
                    // Method 1: オブジェクト生成 + GC実行
                    gen.generateObjects();
                    System.out.println();
                    // Method 2: G1領域のメモリ使用量を出力 + GC実行
                    reporter.printG1RegionMemoryUsage(gen.getHumongousAllocatedBytes());
                }
            }
            case "regions" -> {
                for (int i = 0; i < cli.repeat(); i++) {
                    if (cli.repeat() > 1) {
                        System.out.printf("%n--- イテレーション %d / %d ---%n", i + 1, cli.repeat());
                    }
                    // Method 2: G1領域のメモリ使用量を出力 + GC実行
                    reporter.printG1RegionMemoryUsage();
                }
            }
            default -> {
                System.err.println("不明なコマンド: " + cli.command());
                printHelp();
                System.exit(1);
            }
        }
    }

    /**
     * ヒープ領域にある程度の容量を占めるオブジェクトを生成し、GCを実行する。
     * (直接呼び出し用のファサードメソッド)
     *
     * @param sizeMb            割り当て目標サイズ (MB)
     * @param normalObjectSizeKb 通常オブジェクトサイズ (KB)
     * @param humongousThresholdBytes Humongous判定閾値 (バイト)
     * @param keepAlive         true の場合、GC後もオブジェクト参照を保持
     * @throws InterruptedException GC待機中に割り込みが発生した場合
     */
    public static void generateObjects(int sizeMb, int normalObjectSizeKb,
                                       long humongousThresholdBytes, boolean keepAlive)
            throws InterruptedException {
        ObjectGenerator gen = new ObjectGenerator(
            (long) sizeMb * 1024 * 1024,
            normalObjectSizeKb * 1024,
            humongousThresholdBytes,
            keepAlive
        );
        gen.generateObjects();
    }

    /**
     * G1 GCの各領域のメモリ使用量をstdoutに出力し、GCを実行する。
     * (直接呼び出し用のファサードメソッド)
     *
     * @param verbose true の場合、コレクター別の詳細情報も表示
     * @throws InterruptedException GC待機中に割り込みが発生した場合
     */
    public static void printG1RegionMemoryUsage(boolean verbose) throws InterruptedException {
        G1RegionReporter reporter = new G1RegionReporter(verbose);
        reporter.printG1RegionMemoryUsage();
    }

    private static void printHelp() {
        System.out.println("""
            G1 GC 検証ツール

            使用方法:
              java -XX:+UseG1GC -Xmx512m -Xms128m -XX:G1HeapRegionSize=1m \\
                   -jar gc-investigation.jar <command> [options]

            Commands:
              generate   オブジェクト生成後にG1領域使用量を出力 (両メソッドを順に実行)
              regions    G1領域使用量のみ出力 (GCを実行)
              both       generate と同じ (別名)

            Options:
              --size <MB>         割り当て目標サイズ in MB (default: 100)
              --object-size <KB>  通常オブジェクトサイズ in KB (default: 4)
              --keep              GC後もオブジェクトを保持 (default: GC前に解放)
              --repeat <N>        サイクルの繰り返し回数 (default: 1)
              --verbose           コレクター別の詳細GC情報も表示
              --help, -h          このヘルプを表示

            推奨 JVM フラグ:
              -XX:+UseG1GC              G1 GC を有効化 (必須)
              -Xmx512m                  最大ヒープサイズ
              -Xms128m                  初期ヒープサイズ
              -XX:G1HeapRegionSize=1m   G1領域サイズを明示 (Humongous閾値=512KB)
              -Xlog:gc:stderr:...       GCログを stderr に出力 (stdout をクリーンに保つ)

            実行例:
              # 100MB 割り当て後にG1領域を出力
              java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m \\
                   -jar gc-investigation.jar generate --size 100

              # G1領域のみ確認
              java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m \\
                   -jar gc-investigation.jar regions

              # オブジェクトを保持したままGC (Old Genに残ることを確認)
              java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m \\
                   -jar gc-investigation.jar generate --size 100 --keep --verbose

              # 3回繰り返してGCの挙動を観察
              java -XX:+UseG1GC -Xmx512m -XX:G1HeapRegionSize=1m \\
                   -jar gc-investigation.jar both --size 50 --repeat 3
            """);
    }
}
