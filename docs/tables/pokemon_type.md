# テーブル定義書：POKEMON_TYPE（ポケモンタイプ）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | pokemon_form_id | ポケモンID | int | PK, FK | NO | | POKEMON_FORM.id を参照 |
| 2 | slot | タイプ枠 | smallint | PK | NO | | タイプの保持順序。1から採番 |
| 3 | type_id | タイプID | smallint | FK | NO | | ELEMENT_TYPE.type_id を参照 |

## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_pokemon_type | PRIMARY KEY | pokemon_form_id, slot | 複合主キー | 同一ポケモンの同一タイプ枠の重複を防止 |
| fk_pokemon_type_form | FOREIGN KEY | pokemon_form_id | POKEMON_FORM(id) を参照 | 存在するポケモンのフォルムのみ登録可能 |
| fk_pokemon_type_element | FOREIGN KEY | type_id | ELEMENT_TYPE(type_id) を参照 | 存在するタイプのみ登録可能 |

## 補足事項

- ポケモンのフォルムごとに、保持するタイプを1件ずつ登録する。
- 単タイプのポケモンは `slot = 1` のみ、複合タイプのポケモンは `slot = 1, 2` を使用する。
- ハロウィンなどの効果で3タイプ目を持つ場合に対応できるよう、`slot = 3` も許容する。
- `slot` はタイプの保持順序を表し、タイプの種類そのものを識別する値ではない。
- 同一フォルムの同一タイプ枠には、1種類のタイプのみ登録できる。
- 戦闘中のタイプ変更や追加は、マスタデータを直接更新せず、計算時の状態として管理する。