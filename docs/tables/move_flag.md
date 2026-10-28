# テーブル定義書：MOVE_FLAG（技フラグ紐づけ）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | move_id | 技ID | int | PK, FK | NO | | MOVE.move_id を参照 |
| 2 | move_flag_id | 技フラグID | smallint | PK, FK | NO | | MOVE_FLAG_TYPE.move_flag_id を参照 |

## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_move_flag | PRIMARY KEY | move_id, move_flag_id | 複合主キー | 同一技と同一フラグの重複登録を防止 |
| fk_move_flag_move | FOREIGN KEY | move_id | MOVE(move_id) を参照 | 存在する技のみ登録可能 |
| fk_move_flag_type | FOREIGN KEY | move_flag_id | MOVE_FLAG_TYPE(move_flag_id) を参照 | 存在する技フラグのみ登録可能 |

## 補足事項

- 技と技フラグの多対多関係を管理する中間テーブル。
- 1つの技に複数のフラグを紐づけられる。
- 1つのフラグを複数の技に紐づけられる。
- `(move_id, move_flag_id)` を複合主キーとする。
- フラグの有無は、対応するレコードの存在によって判定する。
- フラグが付与されていない技については、対応するレコードを登録しない。
- 接触技・パンチ技・音技などの分類は、MOVE_FLAG_TYPEマスタで管理する。