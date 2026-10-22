package com.works.keyka.damagecalc.infrastructure.seed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小限のCSV読み込み。ヘッダ行つき、UTF-8(BOMは無視)、CRLF/LF対応、
 * ダブルクォートによる囲み・エスケープ("")・囲み内の改行に対応する。
 * 全項目が空の行(末尾の空行など)は読み飛ばす。
 */
final class CsvTable {

    /** line は、そのレコードがファイル内で始まる行番号(1始まり。ヘッダが1行目)。 */
    record Row(int line, Map<String, String> cells) {
        String get(String column) {
            return cells.getOrDefault(column, "");
        }
    }

    final String file;
    final List<String> header;
    final List<Row> rows;

    private CsvTable(String file, List<String> header, List<Row> rows) {
        this.file = file;
        this.header = header;
        this.rows = rows;
    }

    /** 読めなかったときは null を返し、problems にエラーを積む。 */
    static CsvTable parse(String file, byte[] bytes, SeedProblems problems) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> records = new ArrayList<>();
        List<Integer> startLines = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean cellWasQuoted = false;
        boolean recordHasContent = false;
        int line = 1;
        int recordStart = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    cellWasQuoted = true;
                    recordHasContent = true;
                }
                case ',' -> {
                    cells.add(cell.toString());
                    cell.setLength(0);
                    cellWasQuoted = false;
                    recordHasContent = true;
                }
                case '\r' -> {
                    // CRLF の CR は無視する(LF側で改行を処理)
                }
                case '\n' -> {
                    if (recordHasContent || cell.length() > 0 || cellWasQuoted) {
                        cells.add(cell.toString());
                        records.add(cells);
                        startLines.add(recordStart);
                    }
                    cells = new ArrayList<>();
                    cell.setLength(0);
                    cellWasQuoted = false;
                    recordHasContent = false;
                    line++;
                    recordStart = line;
                }
                default -> {
                    cell.append(c);
                    recordHasContent = true;
                }
            }
        }
        if (inQuotes) {
            problems.error(file, recordStart, "ダブルクォートが閉じられていません");
            return null;
        }
        if (recordHasContent || cell.length() > 0 || cellWasQuoted) {
            cells.add(cell.toString());
            records.add(cells);
            startLines.add(recordStart);
        }
        if (records.isEmpty()) {
            problems.error(file, 0, "ファイルが空です(ヘッダ行がありません)");
            return null;
        }

        List<String> header = new ArrayList<>();
        for (String h : records.get(0)) {
            header.add(h.trim());
        }
        boolean headerOk = true;
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).isEmpty()) {
                problems.error(file, startLines.get(0), (i + 1) + "列目のヘッダ名が空です");
                headerOk = false;
            } else if (header.indexOf(header.get(i)) != i) {
                problems.error(file, startLines.get(0), "ヘッダ名 \"" + header.get(i) + "\" が重複しています");
                headerOk = false;
            }
        }
        if (!headerOk) {
            return null;
        }

        List<Row> rows = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> rec = records.get(r);
            int rowLine = startLines.get(r);
            if (rec.stream().allMatch(s -> s.trim().isEmpty())) {
                continue;
            }
            if (rec.size() > header.size()) {
                boolean extra = rec.subList(header.size(), rec.size()).stream().anyMatch(s -> !s.trim().isEmpty());
                if (extra) {
                    problems.error(file, rowLine, "列数がヘッダより多いです(ヘッダ " + header.size() + " 列、この行 "
                            + rec.size() + " 列)。カンマの混入を確認してください");
                    continue;
                }
            }
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                map.put(header.get(i), i < rec.size() ? rec.get(i).trim() : "");
            }
            rows.add(new Row(rowLine, map));
        }
        return new CsvTable(file, header, rows);
    }

    /**
     * 必須列がすべてあるか確認する。足りなければエラーを積んで false。
     * 列名の末尾に "?" を付けたものは任意列(あってもなくてもよい。読み飛ばす列に使う)。
     * どちらにも当てはまらない余分な列は警告にする。
     */
    boolean requireColumns(SeedProblems problems, String... columns) {
        boolean ok = true;
        List<String> known = new ArrayList<>();
        for (String spec : columns) {
            boolean optional = spec.endsWith("?");
            String col = optional ? spec.substring(0, spec.length() - 1) : spec;
            known.add(col);
            if (!optional && !header.contains(col)) {
                problems.error(file, 1, "必須の列 \"" + col + "\" がありません(ヘッダ: " + String.join(",", header) + ")");
                ok = false;
            }
        }
        for (String col : header) {
            if (!known.contains(col)) {
                problems.warn(file, 1, "未使用の列 \"" + col + "\" は無視します");
            }
        }
        return ok;
    }
}
