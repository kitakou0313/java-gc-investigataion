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

## Young-Only Phase

## Space-Reclamation Phase
