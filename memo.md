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
                - GCの生存回数をObjectごとにカウントしている
            - この時Old regionも増えていく
    - 開放可能な領域を開放する
        - 上記の移動で開放可能になった領域

以下はYoung-Only Phaseの後Old Generation領域の占める割合が閾値（Initiating Heap Occupancy threshold）を超えた時に実行される。

- Concurrent Start
    - Normal young collectionと同時にconcurrent markingをバックグラウンドで実行する
    - collection setの構築
    - Old generation内の全てのLive Objectの発見
    - これはバックグラウンドでの実行であり、アプリケーションの処理の実行と同時に行われる
- Remark
    - アプリケーションを停止させ（Stop the world）、marking処理を完了させる　
        - アプリケーションの処理が実行されているとmarking処理が終了できない（参照グラフが確定しない）ため
    - 以下の処理を実行する
        - marking結果の確定
        - WeakReference, SoftReferenceを処理する
            - どんな処理を？
        - 使用されていないクラスのアンロード
        - すでに空になっているregionの回収
    - 開放すべきOld Regionの選定
        - 次の処理で使用するため
- Cleanup
    - アプリケーションを停止させ（Stop the world）、次のSpace-Reclamation Phaseを実行すべきか開放可能な領域を見て判断する

### Space-Reclamation Phase
- Young, Old世代両方のregionを対象にするGC
- 特徴
    - 回収される最低のOld region数が決まっている
        - 候補region数 / G1MixedGCCountTarget
    - 上記の最低回収数の回収後、停止時間に対してある程度のバッファを残して止まる
    - G1HeapWastePercent未満の開放可能な領域しか持たないregionのみになったら停止

### （不足時のみ）Full GC
- ObjectのLive状態の収集時にメモリの不足が明らかになった場合、Stop the worldして全て世代のregionを対象にGCを行う