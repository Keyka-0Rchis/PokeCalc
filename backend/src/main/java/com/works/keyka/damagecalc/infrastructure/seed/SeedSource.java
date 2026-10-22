package com.works.keyka.damagecalc.infrastructure.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;

/** シードCSVの読み込み元。ディレクトリ・クラスパス・メモリ(テスト用)を差し替えられる。 */
public interface SeedSource {

    /** ファイルが存在しなければ empty。 */
    Optional<byte[]> read(String fileName);

    static SeedSource directory(Path dir) {
        return fileName -> {
            Path p = dir.resolve(fileName);
            if (!Files.isRegularFile(p)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Files.readAllBytes(p));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /** @param base 例: "db/seed" */
    static SeedSource classpath(String base) {
        return fileName -> {
            ClassLoader cl = SeedSource.class.getClassLoader();
            try (InputStream in = cl.getResourceAsStream(base + "/" + fileName)) {
                return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    static SeedSource inMemory(Map<String, String> files) {
        return fileName -> Optional.ofNullable(files.get(fileName)).map(s -> s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 全シードファイルの内容と、読み込みロジックの版から作るチェックサム。
     * CSVを変更すると値が変わり、繰り返しマイグレーションが再実行される。
     */
    static int checksum(SeedSource source, int logicVersion) {
        CRC32 crc = new CRC32();
        crc.update(logicVersion);
        for (String file : MasterSeedParser.FILES) {
            crc.update(file.getBytes(StandardCharsets.UTF_8));
            crc.update(0);
            source.read(file).ifPresent(crc::update);
            crc.update(0);
        }
        return (int) crc.getValue();
    }
}
