-- =====================================================================
-- V1__init.sql  ポケモンチャンピオンズ ダメージ計算ツール 初期スキーマ
--
-- 方針:
--  * ENUM型・CHECK制約は使わない(将来のギミック追加をマイグレーション無しで可能にする)。
--    値の検証はシード読み込み時(MasterSeedParser)とドメイン層で行う。
--  * 倍率は4096基準の整数で保持する(丸め誤差回避)。
--  * マスタの主キーはCSVのidをそのまま使う。一度取り込んだidは変更せず、追加は末尾のみ。
--    (将来パーティ保存などがformのidを参照するため)
--  * マスタ表の中身はR__SeedMaster(繰り返しマイグレーション)がCSVから投入する。
-- =====================================================================

-- ---------------------------------------------------------------------
-- タイプ(id 19 =「ー」(タイプなし)も登録する。「もえつきる」後などの状態に使う)
-- ---------------------------------------------------------------------
create table element_type (
    id    smallint primary key,
    name  text not null unique
);

-- タイプ相性(攻撃タイプ x 防御タイプ)。倍率は 0 / 0.5 / 1 / 2
create table type_effectiveness (
    attack_type_id  smallint      not null references element_type (id),
    defend_type_id  smallint      not null references element_type (id),
    multiplier      numeric(3, 2) not null,
    primary key (attack_type_id, defend_type_id)
);

-- ---------------------------------------------------------------------
-- ポケモン(1行=1フォーム。通常・リージョン・フォルム違い・メガを同列に扱う)
-- ---------------------------------------------------------------------
create table pokemon_form (
    id           int         primary key,
    dex_no       int         not null,              -- 全国図鑑番号
    name         text        not null,              -- 名前
    name_kana    text        not null,              -- 前方一致サジェスト用(KanaNormalizerで正規化)
    region       varchar(16) not null default '',   -- alola / galar / hisui / paldea / ''
    variant_key  varchar(32) not null default '',   -- CSVのform列。male / origin / terras_grass ... / ''
    mega_kind    varchar(8)  not null default '',   -- CSVのmega列。x / y / z ... / ''(メガ以外)
    base_h       smallint    not null,              -- HP
    base_a       smallint    not null,              -- 攻撃
    base_b       smallint    not null,              -- 防御
    base_c       smallint    not null,              -- 特攻
    base_d       smallint    not null,              -- 特防
    base_s       smallint    not null               -- 素早さ
);
create index idx_pokemon_form_dex_no    on pokemon_form (dex_no);
create index idx_pokemon_form_name_kana on pokemon_form (name_kana);
-- 取り込みミスの検知用。キーが重複するデータは投入できない
create unique index uq_pokemon_form_key on pokemon_form (dex_no, region, variant_key, mega_kind);

-- フォームのタイプ(slot: 1=第1タイプ, 2=第2タイプ。単タイプは slot 2 を作らない)
create table pokemon_form_type (
    form_id  int      not null references pokemon_form (id),
    slot     smallint not null,
    type_id  smallint not null references element_type (id),
    primary key (form_id, slot)
);

-- ---------------------------------------------------------------------
-- 特性(同名の特性がありうるため name は unique にしない(じんばいったい)。識別はidで行う)
-- ---------------------------------------------------------------------
create table ability (
    id         int  primary key,
    name       text not null,
    name_kana  text not null
);

-- フォームが取りうる特性(計算画面で選択肢として提示する)
create table pokemon_form_ability (
    form_id     int     not null references pokemon_form (id),
    ability_id  int     not null references ability (id),
    is_hidden   boolean not null default false,
    primary key (form_id, ability_id)
);

-- ---------------------------------------------------------------------
-- 技
-- ---------------------------------------------------------------------
-- 技のフラグの種類(contact, slice, sound, ...)。効果の条件 flag_id:N はこのidを指す
create table move_flag_type (
    id    smallint primary key,
    name  text not null unique
);

create table move (
    id         int      primary key,
    name       text     not null,
    name_kana  text     not null,
    type_id    smallint not null references element_type (id),
    category   varchar(16) not null,    -- PHYSICAL / SPECIAL / STATUS
    power      smallint,                -- 変動威力・威力なしは null
    min_hits   smallint not null default 1, -- 連続技の最小ヒット数。単発技は 1
    max_hits   smallint not null default 1, -- 連続技の最大ヒット数。単発技は 1
    rule_key   varchar(48)              -- 特殊裁定(MoveRule)の識別子。通常技は null
);
create index idx_move_name_kana on move (name_kana);

create table move_flag (
    move_id  int      not null references move (id),
    flag_id  smallint not null references move_flag_type (id),
    primary key (move_id, flag_id)
);

-- ---------------------------------------------------------------------
-- 持ち物
-- ---------------------------------------------------------------------
create table item (
    id         int  primary key,
    name       text not null,
    name_kana  text not null
);

-- ---------------------------------------------------------------------
-- 効果(特性・持ち物)。仕様は docs/master-data-spec.md を参照
--   seq             : CSVの行順。同一phase内の適用順に使う
--   effect_target   : own_move_power, damage, weather ... (RULEで対象なしの場合は null)
--   kind            : MULTIPLY / REPLACE / RULE
--   multiplier_4096 : 4096 = 1.0倍。REPLACEでは置き換え後の絶対値。対象なしは null
--   conditions      : 条件式(';'区切りでAND)。無条件は null
--   phase           : power_start / power_final / status_final / damage_final。無い場合は null
--   params          : from / to / rule など条件ではないパラメータ(jsonb)
-- ---------------------------------------------------------------------
create table ability_effect (
    id               bigint generated always as identity primary key,
    ability_id       int         not null references ability (id),
    seq              int         not null,
    effect_target    varchar(32),
    kind             varchar(16) not null,
    multiplier_4096  int,
    conditions       text,
    phase            varchar(16),
    params           jsonb
);
create index idx_ability_effect_ability on ability_effect (ability_id, seq);

create table item_effect (
    id               bigint generated always as identity primary key,
    item_id          int         not null references item (id),
    seq              int         not null,
    effect_target    varchar(32),
    kind             varchar(16) not null,
    multiplier_4096  int,
    conditions       text,
    phase            varchar(16),
    params           jsonb
);
create index idx_item_effect_item on item_effect (item_id, seq);

-- ---------------------------------------------------------------------
-- フォーム変化(メガシンカ等。将来 DYNAMAX / TERASTAL / Z技 等を kind で追加)
-- メガ行の元フォームは、シード読み込み時に dex_no・region・variant_key が一致する行から解決する
-- ---------------------------------------------------------------------
create table transformation (
    from_form_id     int         not null references pokemon_form (id),
    to_form_id       int         not null references pokemon_form (id),
    kind             varchar(24) not null,   -- MEGA, (将来) DYNAMAX, TERASTAL ...
    trigger_item_id  int         references item (id),
    primary key (from_form_id, to_form_id, kind)
);
