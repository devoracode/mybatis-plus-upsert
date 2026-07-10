package io.github.devoracode.upsert.core.fill;

import org.apache.ibatis.session.Configuration;

/**
 * No-op {@link UpsertFieldFillHandler} used when auto-fill is disabled
 * ({@code mybatis-plus.upsert.auto-fill=false}).
 *
 * <p>Preserves backward-compatible behavior: no field pre-filling is performed
 * before the upsert statement runs.
 *
 * @author devoracode
 * @since 1.5.0
 */
public final class NoOpFillHandler implements UpsertFieldFillHandler {

    @Override
    public void fillBeforeUpsert(Object entity, Configuration configuration) {
        // intentionally empty
    }
}
