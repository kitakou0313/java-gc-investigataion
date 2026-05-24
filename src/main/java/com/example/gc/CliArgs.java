package com.example.gc;

/**
 * コマンドライン引数を保持するレコード。
 */
public record CliArgs(
    String command,
    int sizeMb,
    int objectSizeKb,
    boolean keepAlive,
    int repeat,
    boolean verbose,
    boolean showHelp
) {
    /**
     * コマンドライン引数を解析して {@link CliArgs} を生成する。
     *
     * @param args main メソッドの引数
     * @return 解析結果
     */
    public static CliArgs parse(String[] args) {
        String command = "generate"; // デフォルトコマンド
        int sizeMb = 100;
        int objectSizeKb = 4;
        boolean keepAlive = false;
        int repeat = 1;
        boolean verbose = false;
        boolean showHelp = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "generate", "regions", "both" -> command = args[i];
                case "--size" -> {
                    if (i + 1 < args.length) {
                        sizeMb = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("エラー: --size には値が必要です");
                        showHelp = true;
                    }
                }
                case "--object-size" -> {
                    if (i + 1 < args.length) {
                        objectSizeKb = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("エラー: --object-size には値が必要です");
                        showHelp = true;
                    }
                }
                case "--repeat" -> {
                    if (i + 1 < args.length) {
                        repeat = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("エラー: --repeat には値が必要です");
                        showHelp = true;
                    }
                }
                case "--keep" -> keepAlive = true;
                case "--verbose", "-v" -> verbose = true;
                case "--help", "-h" -> showHelp = true;
                default -> {
                    System.err.println("警告: 不明な引数: " + args[i]);
                    showHelp = true;
                }
            }
        }

        return new CliArgs(command, sizeMb, objectSizeKb, keepAlive, repeat, verbose, showHelp);
    }
}
