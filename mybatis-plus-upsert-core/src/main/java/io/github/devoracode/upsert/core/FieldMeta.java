package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

/*
 * Per-field upsert metadata: column name, property name, and whether SQL generation
 * must apply a null/empty-string dynamic check.
 *
 * When dynamic=true, the field is wrapped in <if test="..."> inside INSERT column/value
 * lists or UPDATE SET clause, matching MP's native FieldStrategy-controlled behavior.
 */
@Getter
@Builder
public class FieldMeta {

    private String column;

    private String property;

    private boolean dynamic;

    private boolean checkEmpty;
}
