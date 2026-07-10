package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.fill.UpsertFieldFillHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

import java.util.Collection;
import java.util.Map;

/**
 * Wraps a delegate {@link SqlSource} and invokes the {@link UpsertFieldFillHandler}
 * on entity objects before the SQL is bound.
 *
 * <p>For single-row upsert ({@code upsert(entity)}), the entity is extracted from
 * the parameter map under the key {@code "et"}. For batch upsert
 * ({@code upsertBatch(list)}), the collection is extracted under {@code "list"}
 * and each element is filled.
 *
 * <p>This approach works because the parameter map holds references to the same
 * entity instances the user passed in, so mutations (field fills) are visible
 * when the delegate {@code SqlSource} builds its {@link BoundSql}.
 *
 * @author devoracode
 * @since 1.5.0
 */
final class FillableSqlSource implements SqlSource {

    private final SqlSource delegate;
    private final UpsertFieldFillHandler fillHandler;
    private final boolean batch;
    private final Configuration configuration;

    /**
     * Creates a new fillable wrapper.
     *
     * @param delegate      the underlying SqlSource that produces the actual SQL
     * @param fillHandler   the field fill handler (must not be null)
     * @param batch         whether this SqlSource is for batch upsert (extracts {@code list} param)
     * @param configuration the MyBatis configuration (must not be null)
     */
    FillableSqlSource(SqlSource delegate, UpsertFieldFillHandler fillHandler, boolean batch, Configuration configuration) {
        this.delegate = delegate;
        this.fillHandler = fillHandler;
        this.batch = batch;
        this.configuration = configuration;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        fillEntities(parameterObject);
        return delegate.getBoundSql(parameterObject);
    }

    @SuppressWarnings("unchecked")
    private void fillEntities(Object parameterObject) {
        if (parameterObject instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) parameterObject;
            if (batch) {
                Object list = paramMap.get("list");
                if (list instanceof Collection) {
                    for (Object entity : (Collection<?>) list) {
                        fillHandler.fillBeforeUpsert(entity, configuration);
                    }
                }
            } else {
                Object entity = paramMap.get("et");
                if (entity != null) {
                    fillHandler.fillBeforeUpsert(entity, configuration);
                }
            }
        } else {
            fillHandler.fillBeforeUpsert(parameterObject, configuration);
        }
    }
}
