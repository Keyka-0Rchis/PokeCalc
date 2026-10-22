package com.works.keyka.damagecalc.infrastructure.seed;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * シードCSVの検証用コマンド。DBなしで実行できる。
 *
 * <pre>
 *   validate  [dir]                 CSVを検証し、問題を行番号つきで表示する(エラーがあれば終了コード1)
 *   dump-sql  [dir] [出力ファイル]   投入されるSQLを書き出す(既定: build/seed.sql)
 * </pre>
 * dir の既定値は src/main/resources/db/seed。
 */
public final class SeedValidationMain {

    private SeedValidationMain() {
    }

    public static void main(String[] args) throws IOException {
        // 日本語のメッセージが端末の文字コードで化けないよう、出力をUTF-8に固定する
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        String command = args.length > 0 ? args[0] : "validate";
        Path dir = Path.of(args.length > 1 ? args[1] : "src/main/resources/db/seed");
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.directory(dir));
        SeedProblems problems = result.problems();
        System.out.print(problems.format());

        if (problems.hasErrors()) {
            System.out.println("\n検証に失敗しました: エラー " + problems.errors().size() + " 件、警告 "
                    + problems.warnings().size() + " 件");
            System.exit(1);
        }
        SeedData d = result.data();
        System.out.println("検証OK(エラー 0 件、警告 " + problems.warnings().size() + " 件)");
        System.out.println("  type " + d.types().size() + " / type_effectiveness " + d.typeChart().size()
                + " / ability " + d.abilities().size() + " / item " + d.items().size()
                + " / move " + d.moves().size() + " / move_flag " + d.moveFlags().size());
        System.out.println("  ability_effect " + d.abilityEffects().size() + " / item_effect " + d.itemEffects().size()
                + " / pokemon_form " + d.forms().size() + " / transformation " + d.transformations().size());

        if (command.equals("dump-sql")) {
            Path out = Path.of(args.length > 2 ? args[2] : "build/seed.sql");
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            List<SqlBatch> plan = SeedPlan.build(d);
            Files.writeString(out, SeedSqlText.render(plan), StandardCharsets.UTF_8);
            System.out.println("SQLを書き出しました: " + out);
        }
    }
}
