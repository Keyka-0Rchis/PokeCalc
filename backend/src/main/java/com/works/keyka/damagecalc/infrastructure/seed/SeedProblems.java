package com.works.keyka.damagecalc.infrastructure.seed;

import java.util.ArrayList;
import java.util.List;

/** シードCSVの検証で見つかった問題の一覧。エラーが1件でもあれば取り込みは行わない。 */
public final class SeedProblems {

    public enum Severity { ERROR, WARNING }

    /** line が 0 のときは、ファイル全体またはファイル間の整合性に関する問題。 */
    public record Problem(Severity severity, String file, int line, String message) {
        @Override
        public String toString() {
            String where = line > 0 ? file + ":" + line : file;
            return severity + " " + where + " " + message;
        }
    }

    private final List<Problem> problems = new ArrayList<>();

    public void error(String file, int line, String message) {
        problems.add(new Problem(Severity.ERROR, file, line, message));
    }

    public void warn(String file, int line, String message) {
        problems.add(new Problem(Severity.WARNING, file, line, message));
    }

    public List<Problem> all() {
        return List.copyOf(problems);
    }

    public List<Problem> errors() {
        return problems.stream().filter(p -> p.severity() == Severity.ERROR).toList();
    }

    public List<Problem> warnings() {
        return problems.stream().filter(p -> p.severity() == Severity.WARNING).toList();
    }

    public boolean hasErrors() {
        return problems.stream().anyMatch(p -> p.severity() == Severity.ERROR);
    }

    /** 1行1件の文字列。エラーを先に出す。 */
    public String format() {
        StringBuilder sb = new StringBuilder();
        for (Problem p : errors()) {
            sb.append(p).append('\n');
        }
        for (Problem p : warnings()) {
            sb.append(p).append('\n');
        }
        return sb.toString();
    }
}
