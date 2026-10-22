package com.works.keyka.damagecalc.infrastructure.seed;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterSeedParserTest {

    private static final SeedSource REAL = SeedSource.classpath("db/seed");

    /** 実際のCSVを読み込み、指定の変更を加えたメモリ上のコピーを作る。 */
    private static Map<String, String> copyOfRealFiles() {
        Map<String, String> files = new HashMap<>();
        for (String name : MasterSeedParser.FILES) {
            byte[] bytes = REAL.read(name).orElseThrow(() -> new AssertionError(name + " がクラスパスにありません"));
            files.put(name, new String(bytes, StandardCharsets.UTF_8));
        }
        return files;
    }

    private static void appendRow(Map<String, String> files, String file, String row) {
        files.put(file, files.get(file).stripTrailing() + "\n" + row + "\n");
    }

    private static boolean anyError(MasterSeedParser.Result result, String fileAndText) {
        return result.problems().errors().stream().anyMatch(p -> p.toString().contains(fileAndText));
    }

    @Test
    void 実際のCSVはエラーなく読み込める() {
        MasterSeedParser.Result result = MasterSeedParser.parse(REAL);
        assertFalse(result.problems().hasErrors(), result.problems().format());
        SeedData d = result.data();
        assertEquals(19, d.types().size());
        assertEquals(342, d.typeChart().size());
        assertTrue(d.forms().size() > 1000);
        assertFalse(d.transformations().isEmpty());
    }

    @Test
    void フィールド名の誤字は行番号つきで検出される() {
        Map<String, String> files = copyOfRealFiles();
        files.put("ability_effect.csv", files.get("ability_effect.csv").replace("field:electric", "field:electoric"));
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.inMemory(files));
        assertTrue(anyError(result, "ability_effect.csv:"), result.problems().format());
        assertTrue(anyError(result, "field:electoric"), result.problems().format());
    }

    @Test
    void 廃止したapply_flagは受け付けない() {
        Map<String, String> files = copyOfRealFiles();
        appendRow(files, "ability_effect.csv", "999,10,damage,MULTIPLY,0,apply_flag:on,damage_final");
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.inMemory(files));
        assertTrue(anyError(result, "apply_flag"), result.problems().format());
    }

    @Test
    void 同じ技に同じフラグを重複指定するとエラー() {
        Map<String, String> files = copyOfRealFiles();
        appendRow(files, "move_flag.csv", "99,1,1");
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.inMemory(files));
        assertTrue(anyError(result, "move_flag.csv:"), result.problems().format());
        assertTrue(anyError(result, "重複"), result.problems().format());
    }

    @Test
    void 存在しない特性idとメガの元フォーム欠落を一度に報告する() {
        Map<String, String> files = copyOfRealFiles();
        appendRow(files, "pokemon.csv", "99998,9998,x,,,メガテスト,1,19,1,,,,50,50,50,50,50,50");
        appendRow(files, "pokemon.csv", "99999,9999,,,,テスト,1,19,9999,,,,50,50,50,50,50,50");
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.inMemory(files));
        assertTrue(anyError(result, "ability.csv にありません"), result.problems().format());
        assertTrue(anyError(result, "元フォームが見つかりません"), result.problems().format());
        assertTrue(result.problems().errors().size() >= 2);
    }

    @Test
    void 名前にセル内改行が入っているとエラー() {
        Map<String, String> files = copyOfRealFiles();
        appendRow(files, "pokemon.csv", "99997,9997,,,,\"テスト \n (あ)\",1,19,1,,,,50,50,50,50,50,50");
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.inMemory(files));
        assertTrue(anyError(result, "pokemon.csv:"), result.problems().format());
        assertTrue(anyError(result, "テスト \\n (あ)"), result.problems().format());
    }

    @Test
    void ファイルが無ければ報告する() {
        Map<String, String> files = copyOfRealFiles();
        files.remove("move.csv");
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.inMemory(files));
        assertTrue(anyError(result, "move.csv ファイルが見つかりません"), result.problems().format());
    }

    @Test
    void 防御側がタイプなしなら等倍以外はエラー() {
        Map<String, String> files = copyOfRealFiles();
        files.put("type_effective.csv", files.get("type_effective.csv").replace("\n1,19,1", "\n1,19,2"));
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.inMemory(files));
        assertTrue(anyError(result, "タイプなし"), result.problems().format());
    }

    @Test
    void 生成されるSQLは何度でも実行できる形で先頭と末尾が決まっている() {
        MasterSeedParser.Result result = MasterSeedParser.parse(REAL);
        String sql = SeedSqlText.render(SeedPlan.build(result.data()));
        assertTrue(sql.startsWith("begin;"));
        assertTrue(sql.trim().endsWith("commit;"));
        assertTrue(sql.contains("on conflict (id) do update"));
    }
}
