# テーブル定義書：POKEMON_FORM（ポケモン一覧）

## カラム定義
|列番号|物理名|論理名|データ型|キー|NULL|デフォルト値|備考|
|---|---|---|---|---|---|---|---|
|1|pokemon_id|ID|int|PK|NO||識別用ID|
|2|dex_no|全国図鑑番号|int||NO||全国図鑑番号|
|3|region|リージョンフォルム|varchar(16)||YES||リージョンフォルムの区分。alola / galar / hisui / paldea|
|4|variant_key|フォルム|varchar(32)||YES||リージョンフォルム以外のフォルムキー|
|5|mega_kind|メガシンカ|varchar(1)||YES||メガシンカの形態。通常メガシンカはx表記。メガシンカポケモン以外はNULL|
|6|name|名前|text||NO||ポケモン名|
|7|name_kana|なまえ|text||NO||検索用ポケモン名（前方一致検索用。フォルムに漢字を含むものがあるため）|
|8|base_h|HP種族値|smallint||NO|||
|9|base_a|攻撃種族値|smallint||NO|||
|10|base_b|防御種族値|smallint||NO|||
|11|base_c|特攻種族値|smallint||NO|||
|12|base_d|特防種族値|smallint||NO|||
|13|base_s|素早さ種族値|smallint||NO|||

## 制約定義
| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_pokemon_form | PRIMARY KEY | pokemon_id | 一意、NOT NULL | ポケモンフォルムの識別ID |
| uq_pokemon_form_identity | UNIQUE | dex_no, region, variant_key, mega_kind | NULLS NOT DISTINCT | 同一フォルムの重複登録を防止 |
| ck_pokemon_form_mega_kind | CHECK | mega_kind | `mega_kind IN ('x', 'y', 'z')` または NULL | メガシンカの区分値を制限 |
| ck_pokemon_form_dex_no | CHECK | dex_no | `dex_no > 0` | 全国図鑑番号は正の整数 |
| ck_pokemon_form_base_stats | CHECK | base_h, base_a, base_b, base_c, base_d, base_s | 各種族値が0以上 | 種族値の不正値を防止 |

### 補足事項
- `region`、`variant_key`、`mega_kind` がNULLの場合は、該当する形態区分を持たないことを表す。
- `UNIQUE NULLS NOT DISTINCT` により、NULL同士も同一値として扱い、同じフォルムの重複を防止する。
- `mega_kind = 'x'` は通常メガシンカとメガXを共通で表す。
- 主キー `id` はフォルムごとに一意の値を持つ。