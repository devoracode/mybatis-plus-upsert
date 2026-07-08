package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FieldMeta {

    private final String column;
    private final String property;
    private final boolean dynamic;
    private final boolean checkEmpty;
}
