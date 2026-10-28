# テーブル定義書：TYPE_EFFECTIVENESS（タイプ相性）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | attack_type_id | 攻撃側タイプID | smallint | PK, FK | NO | | 攻撃側のタイプID |
| 2 | defend_type_id | 防御側タイプID | smallint | PK, FK | NO | | 防御側のタイプID |
| 3 | multiplier | 相性倍率 | numeric(2,1) | | NO | | タイプ相性による倍率。0 / 0.5 / 1 / 2 |

## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_type_effectiveness | PRIMARY KEY | attack_type_id, defend_type_id | 複合主キー | タイプの組み合わせを一意に識別 |
| fk_type_effectiveness_attack | FOREIGN KEY | attack_type_id | ELEMENT_TYPE(type_id) を参照 | 攻撃側タイプの存在を保証 |
| fk_type_effectiveness_defend | FOREIGN KEY | defend_type_id | ELEMENT_TYPE(type_id) を参照 | 防御側タイプの存在を保証 |
| ck_type_effectiveness_multiplier | CHECK | multiplier | `multiplier IN (0, 0.5, 1, 2)` | 許可する相性倍率を制限 |

## 補足事項

- 攻撃側タイプと防御側タイプの組み合わせごとに、相性倍率を1件保持する。
- 相性倍率は `0`（無効）、`0.5`（いまひとつ）、`1`（等倍）、`2`（ばつぐん）のいずれかとする。
- 複合主キーにより、同一の攻撃側タイプ・防御側タイプの組み合わせを重複登録できない。
- `attack_type_id` と `defend_type_id` は、いずれも `ELEMENT_TYPE.type_id` を参照する。
- 「タイプなし」も `ELEMENT_TYPE` に登録する。相性表は全て等倍とする。
- 防御側が複数タイプを持つ場合は、各防御タイプに対する倍率を計算処理で乗算する。