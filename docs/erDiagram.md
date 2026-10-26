```Marmeid
erDiagram
    POKEMON_FORM }o--|| POKEMON_FORM_TYPE : ""
    POKEMON_FORM }o--|| POKEMON_FORM_ABILITY : ""
    POKEMON_FORM_TYPE ||--|{ ELEMENT_TYPE : ""
    POKEMON_FORM_ABILITY ||--|{ ABILITY : ""
    ABILITY }o--|{ ABILITY_EFFECT : ""
    MOVE ||--o{ MOVE_FLAG : ""
    MOVE_FLAG ||--|| MOVE_FLAG_TYPE : ""
    MOVE ||--|| ELEMENT_TYPE:""
    TYPE_EFFECTIVENESS }|--|{ ELEMENT_TYPE:""
    ITEM ||--|{ ITEM_EFFECT:""

    POKEMON_FORM {
        int id PK
        int dex_no
        text name
        text name_kana
        varchar(16) region
        varchar(32) variant_key
        varchar(8) mega_kind
        smallint base_h
        smallint base_a
        smallint base_b
        smallint base_c
        smallint base_d
        smallint base_s
    }
    ELEMENT_TYPE {
        smallint id
        text name
    }
    TYPE_EFFECTIVENESS{
        smallint attack_type_id
        smallint defend_type_id
        numeric(3,2) multiplier
    }
    POKEMON_FORM_TYPE{
        int form_id
        smallint slot
        smallint type_id
    }
    ABILITY{
        int id
        text name
        text name_kana
    }
    POKEMON_FORM_ABILITY{
        int form_id
        int ability_id
        boolean is_hidden
    }
    MOVE_FLAG_TYPE{
        smallint id
        text name
    }
    MOVE{
        int id
        text name
        text name_kana
        smallint type_id
        varchar(16) category
        smallint power
        smallint min_hits
        smallint max_hits
        varchar(48) rule_key
    }
    MOVE_FLAG{
        int move_id
        smallint flag_id
    }
    ITEM{
        int id
        text name
        text name_kana
    }
    ABILITY_EFFECT{
        bigint id
        int ability_id
        int seq
        varchar(32) effect_target
        varchar(16) kind
        int multiplier_4096
        text conditions
        varchar(16) phase
        jsonb params
    }
    ITEM_EFFECT{
        bigint id
        int item_id
        int seq
        varchar(32) effect_target
        varchar(16) kind
        int multiplier_4096
        text conditions
        varchar(16) phase
        jsonb params
    }
    TRANSFORMATION{
        int form_from_id
        int to_form_id
        varchar(24) kind
        int trigger_item_id
    }
```
