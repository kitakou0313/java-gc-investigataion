# G1 GCについてのメモ

## 領域
- heap領域を同サイズのregionに分ける
- それぞれにEden, Survivor, Old, Humongousの役割を動的に割り当てる
- それぞれ以下の世代に分類される
    - Young
        - Eden
        - Survivor
    - Old
        - Old
        - Humongous

## G1 GCの流れ
以下二つのフェーズで構成される

## Objectの初回生成
- ほとんどの場合Eden領域に配置される
- 閾値以上のサイズの場合はHumongous領域に配置される

### Young-Only Phase
- Normarl Young Collection
    - 頻繁に実施されるGC
    - Young世代の領域のみを対象とする
    - 処理は以下のようになる
        - Eden
            - Live Object（アプリケーションから参照されているObject）はSurvivor領域にコピーされる
                - どうやって判定している？
        - Survivor
            - 一定以上の回数を生き残った場合Old regionに移動する（promotion）

以下はYoung-Only Phaseの後Old Generation領域の占める割合が閾値（Initiating Heap Occupancy threshold）を超えた時に実行される。

- Concurrent Start
- Remark
- Cleanup

Young-Only Phaseの後、Old Generation領域の占める割合が閾値を超えた時に実行される。

### Space-Reclamation Phase

### （不足時のみ）Full GC