package db.migration;

import com.works.keyka.damagecalc.domain.master.effect.EffectConditionParser;
import com.works.keyka.damagecalc.domain.master.effect.EffectKind;
import com.works.keyka.damagecalc.domain.master.effect.EffectPhase;
import com.works.keyka.damagecalc.domain.master.effect.EffectTarget;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.works.keyka.damagecalc.infrastructure.seed.MasterSeedParser;
import com.works.keyka.damagecalc.infrastructure.seed.SeedPlan;
import com.works.keyka.damagecalc.infrastructure.seed.SeedPlanRunner;
import com.works.keyka.damagecalc.infrastructure.seed.SeedSource;

import java.util.logging.Logger;

/**
 * マスタデータ(src/main/resources/db/seed/*.csv)をDBに投入する、繰り返しマイグレーション。
 *
 * <p>名前が R__ で始まるので、V系(V1__init.sql など)の後に実行され、
 * チェックサム(CSVの内容+読み込みロジックの版)が変わったときだけ再実行される。
 * CSVにエラーがあれば、行番号つきのメッセージで起動を止める(中途半端には投入しない)。
 */
public class R__SeedMaster extends BaseJavaMigration {

    /** 読み込みロジック(かな正規化・SQLの作り方など)を変えたら、この数を増やして再投入させる。 */
    private static final int SEED_LOGIC_VERSION = 1;

    // CSVの場所。classpathで指定するので、src/main/resources/db/seed に置くこと。
    private static final String SEED_BASE = "db/seed";
    private static final Logger LOG = Logger.getLogger(R__SeedMaster.class.getName());

    // EffectConditionParser, EffectKind, EffectPhase, EffectTarget の参照を持たせることで、CSVの読み込み時にこれらのenumの値が使われているかを検証する。
    @Override
    public Integer getChecksum() {
        return SeedSource.checksum(SeedSource.classpath(SEED_BASE), SEED_LOGIC_VERSION);
    }

    @Override
    public void migrate(Context context) throws Exception {
        MasterSeedParser.Result result = MasterSeedParser.parse(SeedSource.classpath(SEED_BASE));
        if (result.problems().hasErrors()) {
            throw new FlywayException("シードCSVの検証に失敗しました:\n" + result.problems().format());
        }
        result.problems().warnings().forEach(w -> LOG.warning(w.toString()));
        SeedPlanRunner.run(context.getConnection(), SeedPlan.build(result.data()));
    }
}
