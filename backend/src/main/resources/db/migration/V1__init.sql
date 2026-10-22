-- =====================================================================
-- V1__init.sql  ポケモンチャンピオンズ ダメージ計算ツール 初期スキーマ
-- 方針:
--  * ENUM型・CHECK制約は原則使わない(将来のギミック追加をマイグレーション無しで可能にする)
--    値の検証はアプリケーション側(ドメイン層)で行う
--  * 倍率は4096基準の整数で保持する(丸め誤差回避)
--  * 効果データの条件は condition_type + params(jsonb) で柔軟に保持する
-- =====================================================================

-- ---------------------------------------------------------------------
-- タイプ
-- ---------------------------------------------------------------------
create table element_type (
    id    smallint primary key,
    name  text not null unique          -- 例: ほのお
);

-- タイプ相性(攻撃タイプ x 防御タイプ)。倍率は 0 / 0.5 / 1 / 2
create table type_effectiveness (
    attack_type_id  smallint not null references element_type (id),
    defend_type_id  smallint not null references element_type (id),
    multiplier      numeric(3, 2) not null,
    primary key (attack_type_id, defend_type_id)
);

-- ---------------------------------------------------------------------
-- ポケモン
-- ---------------------------------------------------------------------
create table pokemon (
    id         int primary key,
    dex_no     int  not null,
    name       text not null,
    name_kana  text not null            -- ひらがな。前方一致サジェスト用(正規化はアプリ側)
);
create index idx_pokemon_name_kana on pokemon (name_kana);

-- フォーム(通常・メガ・リージョンフォーム等を同列に扱う)
create table pokemon_form (
    id          int primary key,
    pokemon_id  int  not null references pokemon (id),
    form_name   text not null,          -- 例: 通常, メガ, メガX
    is_default  boolean not null default false,
    is_mega     boolean not null default false,
    base_hp     smallint not null,
    base_atk    smallint not null,
    base_def    smallint not null,
    base_spa    smallint not null,
    base_spd    smallint not null,
    base_spe    smallint not null,
    weight_kg   numeric(6, 1),          -- 重さ依存技(けたぐり等)用
    sort_order  int not null default 0
);
create index idx_pokemon_form_pokemon on pokemon_form (pokemon_id);

-- フォームのタイプ(slot: 1=第1タイプ, 2=第2タイプ)
create table pokemon_form_type (
    form_id  int      not null references pokemon_form (id),
    slot     smallint not null,
    type_id  smallint not null references element_type (id),
    primary key (form_id, slot)
);

-- ---------------------------------------------------------------------
-- 特性
-- ---------------------------------------------------------------------
create table ability (
    id    int primary key,
    name  text not null,
    name_kana text not null
);

-- フォームが取りうる特性(計算画面で選択肢として提示する)
create table pokemon_form_ability (
    form_id     int not null references pokemon_form (id),
    ability_id  int not null references ability (id),
    is_hidden   boolean not null default false,
    primary key (form_id, ability_id)
);

-- ---------------------------------------------------------------------
-- 技
-- ---------------------------------------------------------------------
create table move (
    id         int primary key,
    name       text not null,
    name_kana  text not null,
    type_id    smallint not null references element_type (id),
    category   varchar(16) not null,     -- PHYSICAL / SPECIAL / STATUS (アプリ側で検証)
    power      smallint,                 -- 変動威力・威力なしは null
    min_hits   smallint not null default 1,
    max_hits   smallint not null default 1,
    priority   smallint not null default 0,
    rule_key   varchar(48)               -- 特殊裁定(MoveRule)の識別子。通常技は null
);
create index idx_move_name_kana on move (name_kana);

-- 技の追加情報(CONTACT, SLICING, BALL, WAVE, PUNCH, SOUND, BITE, PULSE ...)
create table move_flag (
    move_id  int         not null references move (id),
    flag     varchar(24) not null,
    primary key (move_id, flag)
);

-- ---------------------------------------------------------------------
-- 持ち物
-- ---------------------------------------------------------------------
create table item (
    id         int primary key,
    name       text not null,
    name_kana  text not null
);

-- 持ち物の効果。1つの持ち物が複数効果を持てる
create table item_effect (
    id               int primary key,
    item_id          int         not null references item (id),
    effect_type      varchar(32) not null,   -- POWER_MOD, ATK_MOD, DEF_MOD, TYPE_BOOST, FINAL_MOD ...
    phase            varchar(32) not null,   -- BASE_POWER, ATTACK_STAT, DEFENSE_STAT, FINAL_DAMAGE ...
    multiplier_4096  int         not null,   -- 4096 = 1.0倍
    condition_type   varchar(32),            -- null = 無条件
    params           jsonb                   -- 条件パラメータ(例: {"typeId": 10})
);
create index idx_item_effect_item on item_effect (item_id);

-- 特性の効果(item_effect と同構造)
create table ability_effect (
    id               int primary key,
    ability_id       int         not null references ability (id),
    effect_type      varchar(32) not null,
    phase            varchar(32) not null,
    multiplier_4096  int         not null,
    condition_type   varchar(32),
    params           jsonb
);
create index idx_ability_effect_ability on ability_effect (ability_id);

-- ---------------------------------------------------------------------
-- フォーム変化(メガシンカ。将来 DYNAMAX / TERASTAL / Z技 等を kind で追加)
-- ---------------------------------------------------------------------
create table transformation (
    from_form_id     int         not null references pokemon_form (id),
    to_form_id       int         not null references pokemon_form (id),
    kind             varchar(24) not null,   -- MEGA, (将来) DYNAMAX, TERASTAL ...
    trigger_item_id  int         references item (id),
    primary key (from_form_id, to_form_id, kind)
);
