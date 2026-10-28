MOVE_FLAG_TYPE(技フラグマスタ)

move_flag_id,技フラグID,smallint,PK,NO,主キー
name,名前,text,,NO,技フラグ名称

nameはUNIQUE# テーブル定義書：MOVE_FLAG_TYPE（技フラグマスタ）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | move_flag_id | 技フラグID | smallint | PK | NO | | 技フラグを識別する主キー |
| 2 | name | 名前 | text | | NO | | 技フラグの名称 |

## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_move_flag_type | PRIMARY KEY | move_flag_id | 一意、NOT NULL | 技フラグIDの一意性を保証 |
| uq_move_flag_type_name | UNIQUE | name | 重複不可 | 同一名称の技フラグの重複登録を防止 |

## 補足事項

- 技が持つ性質を分類するためのマスタテーブル。
- 接触技、パンチ技、音技などの分類を管理する。
- 技との紐づけは `MOVE_FLAG` テーブルで管理する。
- `name` にはUNIQUE制約を設定し、同一名称の重複を防止する。
- フラグの有無は `MOVE_FLAG` に対応レコードが存在するかどうかで判定する。