# テーブル定義書：ABILITY_EFFECT（特性効果）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | ability_id | 特性ID | int | PK, FK | NO | なし | ABILITY(ability_id)を参照 |
| 2 | ability_id_seq | 特性効果連番 | int | PK | NO | 1 | 特性ごとの効果識別用。適用順序は表さない |
| 3 | activation_side | 発動側 | varchar(16) | — | YES | NULL | ATTACK / DEFENSE / BOTH |
| 4 | effect_target | 効果対象 | varchar(32) | — | YES | NULL | 変更対象。RULEの場合はNULL可 |
| 5 | kind | 効果種別 | varchar(16) | — | NO | なし | MULTIPLY / REPLACE / RULE |
| 6 | rule_key | 特殊処理識別子 | varchar(32) | — | YES | NULL | RULEの場合に使用。Java側の専用処理と対応 |
| 7 | multiplier_4096 | 補正倍率 | int | — | YES | NULL | MULTIPLYの場合に使用。4096を1倍とする |
| 8 | phase | 適用フェーズ | varchar(16) | — | YES | NULL | RULEの場合はNULL |
| 9 | conditions | 発動条件 | text | — | YES | NULL | 比較式をセミコロンでAND結合。NULLは無条件 |
| 10 | value | 設定値 | text | — | YES | NULL | REPLACEの場合に使用。effect_targetに応じて解釈 |


## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_ability_effect | PRIMARY KEY | ability_id, ability_id_seq | 複合主キー | 特性内の効果を一意に識別 |
| fk_ability_effect_ability | FOREIGN KEY | ability_id | REFERENCES ABILITY(ability_id) | 特性マスタとの関連 |
| ck_ability_effect_seq | CHECK | ability_id_seq | ability_id_seq >= 1 | 連番は1以上 |
| ck_ability_effect_kind | CHECK | kind | IN ('MULTIPLY', 'REPLACE', 'RULE') | 効果種別の区分 |
| ck_ability_effect_phase | CHECK | phase | IN ('calc_start', 'status_final', 'power_first', 'power_second', 'damage_final') OR phase IS NULL | 適用フェーズの区分 |
| ck_ability_effect_multiplier | CHECK | multiplier_4096 | multiplier_4096 >= 0 OR multiplier_4096 IS NULL | 0倍を許容 |

### 効果種別別制約

- **MULTIPLY**
  - `multiplier_4096`：NOT NULL
  - `value`：NULL
  - `rule_key`：NULL
  - `phase`：NOT NULL
  - `effect_target`：NOT NULL
- **REPLACE**
  - `multiplier_4096`：NULL
  - `value`：NOT NULL
  - `rule_key`：NULL
  - `phase`：NOT NULL
  - `effect_target`：NOT NULL
- **RULE**
  - `multiplier_4096`：NULL
  - `value`：NULL
  - `rule_key`：NOT NULL
  - `phase`：NULL
  - `effect_target`：NULL可

## 補足事項

### 1. 発動側（activation_side）

| 値 | 意味 |
|---|---|
| ATTACK | 攻撃側の特性として評価 |
| DEFENSE | 防御側の特性として評価 |
| BOTH | 攻撃側・防御側のいずれでも評価 |

effect_target中の `own` は特性の所有者、`opponent` はその相手を指す。

技関連の条件キーは、原則として今回計算に使用する技を参照するため、攻撃側・防御側を区別しない。

### 2. 効果種別（kind）

| 値 | 意味 |
|---|---|
| MULTIPLY | effect_targetにmultiplier_4096の倍率を適用する |
| REPLACE | effect_targetをvalueで指定した値に置き換える |
| RULE | rule_keyに対応するJavaの専用処理を実行する |

`multiplier_4096` は4096を1倍とする整数表現。

例：0倍＝0、1.2倍＝4915、1.5倍＝6144、2倍＝8192。

### 3. 適用フェーズ（phase）

| 値 | 適用タイミング |
|---|---|
| calc_start | 計算開始時の前処理。天候などの変更 |
| status_final | 能力実数値の補正 |
| power_first | 技タイプなど、威力倍率補正に先立つ変更 |
| power_second | 技威力の倍率補正 |
| damage_final | 最終ダメージへの補正 |

各フェーズでは、攻撃側・防御側の特性および持ち物について発動条件を評価し、適用可能な効果を確定してから当該フェーズの効果を反映する。

RULEはフェーズ管理の対象外とし、専用ユースケースから実行する。実行タイミングはユースケース側で制御する。

### 4. 発動条件（conditions）

条件式は以下の形式で記述する。

`条件キー 演算子 比較値`

複数条件は `;` で区切り、すべてAND条件として評価する。

使用する演算子：

| 演算子 | 意味 |
|---|---|
| = | 一致 |
| != | 不一致 |
| < | 未満 |
| <= | 以下 |
| > | 超過 |
| >= | 以上 |

記述例：

| 条件 | 記述例 |
|---|---|
| 技タイプが4 | `move_type_id=4` |
| 天候が雨 | `weather=rain` |
| 技が音技 | `move_flag_id=3` |
| 技威力が60以下 | `move_power<=60` |
| HP割合が1/3以下 | `own_hp_ratio<=1/3` |
| 状態異常がある | `own_bad_condition=any` |
| 毒状態かつ物理技 | `own_bad_condition=poisoned;move_category=PHYSICAL` |
| タイプ相性倍率が1未満 | `type_effectiveness<1` |
| 特性変更前の技タイプが1 | `original_move_type_id=1` |

各条件キーの参照先および評価方法はJava側で定義する。

`original_move_type_id` は、技固有の特殊裁定を反映した後、特性によるタイプ変更を適用する前の技タイプを保持する。

条件式がNULLの場合は特性が無効化されていない限り無条件で適用対象となる。

### 5. 設定値（value）

REPLACEで使用する変更後の値を保持する。

変更対象は `effect_target` で特定されるため、valueには変更後の値のみを格納する。

| effect_target | value例 | 意味 |
|---|---|---|
| own_move_type | `4` | 技タイプをID 4に変更 |
| weather | `none` | 計算上の天候をなしに変更 |
| critical_rate | `9216` | 急所倍率を4096基準の2.25倍に変更 |

valueはtext型とし、effect_targetに応じてJava側で型変換・妥当性検証を行う。

### 6. 特殊処理（rule_key）

RULEに該当する効果は、rule_keyに対応するJava側の専用処理で実現する。

RULEの実行順序・実行条件・変更対象は、専用ユースケース側で管理する。

### 7. 特性効果連番（ability_id_seq）

同一特性に複数の効果が存在する場合、1から順に採番する。

連番はレコードの識別用であり、効果の実行順序を保証しない。

適用順序はphaseおよび計算処理側の制御に従う。

### 8. 技タイプ変更と威力補正

スキン系特性は、技タイプ変更と威力補正を別レコードとして管理する。

処理順序：

1. 技情報取得
2. 技固有の特殊裁定
3. original_move_type_idを保存
4. power_firstの効果判定・適用
5. power_secondの効果判定・適用
6. ダメージ計算へ移行

これにより、技タイプ変更後でも、変更前のタイプを条件とする威力補正を適用できる。

### 9. マスタ登録例

| 特性 | activation_side | kind | effect_target | conditions | phase | value | multiplier_4096 |
|---|---|---|---|---|---|---|---|
| ちくでん | DEFENSE | MULTIPLY | damage | `move_type_id=4` | damage_final | NULL | 0 |
| すいすい | BOTH | MULTIPLY | own_status_s | `weather=rain` | status_final | NULL | 8192 |
| てつのこぶし | ATTACK | MULTIPLY | own_move_power | `move_flag_id=3` ※パンチフラグIDに置換 | power_second | NULL | 4915 |
| エレキスキン（タイプ変更） | ATTACK | REPLACE | own_move_type | `move_type_id=1` | power_first | `4` | NULL |
| エレキスキン（威力補正） | ATTACK | MULTIPLY | own_move_power | `original_move_type_id=1` | power_second | NULL | 4915 |
| エアロック | BOTH | REPLACE | weather | NULL | calc_start | `none` | NULL |

※登録例のタイプID・フラグID・倍率は、実際のマスタおよび対象ゲームの仕様に合わせて確認すること。

## 設計上の留意点

- 複数の対象を持つ特性は、原則として効果レコードを分割する。
- 同一フェーズ内の条件判定は、そのフェーズ開始時点の計算コンテキストを基準とする。
- `conditions` の比較値は条件キーごとに型変換・妥当性検証する。
- `value` はREPLACE専用、`multiplier_4096` はMULTIPLY専用、`rule_key` はRULE専用とする。
- 複雑な処理や汎用条件式で表現できない効果はRULEで扱う。
- `ability_id_seq` は実行優先順位として使用しない。というか順不同ということにする。そこまで厳密には無理。今後に期待。