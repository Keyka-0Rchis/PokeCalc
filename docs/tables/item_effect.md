# テーブル定義書：ITEM_EFFECT（持ち物効果）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | item_id | 持ち物ID | int | PK, FK | NO | | ITEM.item_id を参照 |
| 2 | item_id_seq | 特性ID連番 | int | PK | NO | 1 | 同一の持ち物に複数の効果を設定するための連番 |
| 3 | activation_side | 発動側 | varchar(16) | — | YES | NULL | ATTACK / DEFENSE / BOTH |
| 4 | effect_target | 効果対象 | varchar(32) | | NO | | own_move_power、own_status_sなど、計算処理に影響を与える対象を指定 |
| 5 | kind | 種別 | varchar(16) | | NO | | MULTIPLY / REPLACE / RULE |
| 6 | multiplier_4096 | 倍率 | int | | YES | | 4096 = 1.0倍。対象なしはNULL |
| 7 | conditions | 条件式 | text | | YES | | `;` 区切りでAND条件を指定 |
| 8 | phase | タイミング | varchar(16) | | YES | | power_start / power_final / status_final / damage_final |
| 9 | params | パラメータ | jsonb | | YES | | from / to / ruleなど、条件ではないパラメータを保持 |

## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_item_effect | PRIMARY KEY | item_id, item_id_seq | 複合主キー | 同一特性に複数の効果を登録可能。主キー構成は要検討 |
| fk_item_effect_item | FOREIGN KEY | item_id | item(item_id) を参照 | 存在する特性のみ登録可能 |
| ck_item_effect_seq | CHECK | item_id_seq | `item_id_seq >= 1` | 連番は1以上 |
| ck_item_effect_kind | CHECK | kind | `kind IN ('MULTIPLY', 'REPLACE', 'RULE')` | 効果種別を制限 |

## 補足事項

### 条件とパラメータ

- `conditions` はセミコロン（`;`）区切りのAND条件として評価する。
- `phase` は効果を適用する計算タイミングを表す。
- `params` はJSONB形式で保持し、効果種別に応じた追加情報を設定する。
- 効果の解釈と実行はバックエンドの計算処理で行う。

### 主キー設計

- 現時点では `(item_id, item_id_seq)` を複合主キーとする。
- 単独の効果IDを主キーとして設ける方式も検討対象とする。
- `item_id_seq` は特性ごとに1から採番する。
- `item_id_seq` のデフォルト値は1とする。ただし、同一特性の2件目以降は明示的な採番が必要。

### 設計上の留意事項

- `conditions`、`phase`、`params` の具体的な構文および必須条件は、計算エンジン側の仕様と合わせて定義する。
- 同一特性に複数の効果がある場合、効果の適用順序は別途定義する。
- csvでの記法ルールは検討の余地あり。
