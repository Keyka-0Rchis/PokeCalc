テーブル名：POKEMON_ABILITY

pokemon_form_id,ポケモンID,int,PK FK,NO,ポケモン一覧.ポケモンID
ability_id,特性ID,int,PK FK,NO,特性.特性ID
is_dream,夢特性フラグ,boolean,PK,夢特性であること（初期値false）

特性は通常特性と夢特性で同一の特性を持つポケモンがいるため、3項目で主キーとする必要がある。
無効フラグはjava及びフロントエンドでは持つがDBへの永続化は不要と判断# テーブル定義書：POKEMON_ABILITY（ポケモン特性）

## カラム定義

| 列番号 | 物理名 | 論理名 | データ型 | キー | NULL | デフォルト値 | 備考 |
|---|---|---|---|---|---|---|---|
| 1 | pokemon_form_id | ポケモンID | int | PK, FK | NO | | POKEMON_FORM.id を参照 |
| 2 | ability_id | 特性ID | int | PK, FK | NO | | ABILITY.ability_id を参照 |
| 3 | is_dream | 夢特性フラグ | boolean | PK | NO | false | 夢特性の場合はtrue、通常特性の場合はfalse |

## 制約定義

| 制約名 | 制約種別 | 対象カラム | 定義・条件 | 備考 |
|---|---|---|---|---|
| pk_pokemon_ability | PRIMARY KEY | pokemon_form_id, ability_id, is_dream | 複合主キー | 同一特性を通常特性・夢特性の両方に登録可能 |
| fk_pokemon_form_id | FOREIGN KEY | pokemon_form_id | POKEMON_FORM.pokemon_id を参照 | 存在するポケモンのみ登録可能 |
| fk_ability_id | FOREIGN KEY | ability_id | ABILITY.ability_id を参照 | 存在する特性のみ登録可能 |

## 補足事項

- ポケモンのフォルムごとに、保有可能な特性を登録する。
- `is_dream = false` は通常特性、`is_dream = true` は夢特性を表す。
- 同一ポケモンが同一特性を通常特性と夢特性の両方に持つ場合、それぞれ別レコードとして登録する。
- `is_dream` のデフォルト値は `false` とする。
- 特性の無効化状態は戦闘中の一時的な状態としてJavaおよびフロントエンドで管理し、DBには永続化しない。
- 特性名は一意とは限らない(じんばいったい)ため、特性の識別には `ABILITY.ability_id` を使用する。
- 特性名は公式でひらがなで統一されているため、検索用の文字列は永続化しない。
