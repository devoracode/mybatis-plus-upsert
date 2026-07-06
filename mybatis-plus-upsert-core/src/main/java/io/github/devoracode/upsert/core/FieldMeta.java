package io.github.devoracode.upsert.core;

public class FieldMeta {

    private final String column;
    private final String property;
    private final boolean dynamic;
    private final boolean checkEmpty;

    private FieldMeta(Builder builder) {
        this.column = builder.column;
        this.property = builder.property;
        this.dynamic = builder.dynamic;
        this.checkEmpty = builder.checkEmpty;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getColumn() {
        return column;
    }

    public String getProperty() {
        return property;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public boolean isCheckEmpty() {
        return checkEmpty;
    }

    public static class Builder {
        private String column;
        private String property;
        private boolean dynamic;
        private boolean checkEmpty;

        public Builder column(String column) {
            this.column = column;
            return this;
        }

        public Builder property(String property) {
            this.property = property;
            return this;
        }

        public Builder dynamic(boolean dynamic) {
            this.dynamic = dynamic;
            return this;
        }

        public Builder checkEmpty(boolean checkEmpty) {
            this.checkEmpty = checkEmpty;
            return this;
        }

        public FieldMeta build() {
            return new FieldMeta(this);
        }
    }
}
