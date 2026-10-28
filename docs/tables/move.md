# テーブル定義書：MOVE（技）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | move_id | 技ID | int | PK | NO | | 技を識別する主キー |
| 2 | name | 名前 | text | | NO | | 技の名前 |
| 3 | name_kana | なまえ | text | | NO | | 検索用のひらがな表記。カタカナで始まる技にも対応 |
| 4 | type_id | タイプID | smallint | FK | NO | | ELEMENT_TYPE.type_id を参照 |
| 5 | category | カテゴリ | varchar(16) | | NO | | PHYSICAL / SPECIAL / STATUS。物理・特殊・変化を表す |
| 6 | power | 威力 | smallint | | YES | | 技の基本威力。変動威力の場合はNULL |
| 7 | min_hits | 最小ヒット数 | smallint | | NO | 1 | 連続技の最小ヒット数 |
| 8 | max_hits | 最大ヒット数 | smallint | | NO | 1 | 連続技の最大ヒット数 |
| 9 | move_rule_key | 技ルール | varchar(48) | | YES | | 特殊裁定を識別するキー。通常技ではNULL |

## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_move | PRIMARY KEY | move_id | 一意、NOT NULL | 技IDの一意性を保証 |
| fk_move_type | FOREIGN KEY | type_id | ELEMENT_TYPE(type_id) を参照 | 存在するタイプのみ登録可能 |
| ck_move_category | CHECK | category | `category IN ('PHYSICAL', 'SPECIAL', 'STATUS')` | 技カテゴリを制限 |
| ck_move_power | CHECK | power | `power >= 0` | NULLは許容 |
| ck_move_hits | CHECK | min_hits, max_hits | `min_hits >= 1 AND max_hits >= min_hits` | ヒット数の範囲を保証 |

## 補足事項

### 技の威力

- `power` は技の基本威力を保持する。
- 変動威力の技は `power` をNULLとする。
- 変動威力の算出処理は初期実装では行わず、フロントエンドからユーザーが威力を指定する。
- フロントエンドでは固定威力の技についても威力を変更可能とする。
- ユーザーが威力を指定した場合は、計算時に限り入力値を優先する。
- 入力値による上書きは計算用データに対して行い、MOVEマスタの値は更新しない。
- 重さ依存などの変動威力の自動算出は、将来的な拡張対象