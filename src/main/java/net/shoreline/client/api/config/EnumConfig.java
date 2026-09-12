package net.shoreline.client.api.config;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnumConfig<T extends Enum<?>> extends Config<T> {

    private T[] values;
    private int index;

    public EnumConfig(String name, String description) {
        super(name, description);
    }

    @Override
    public JsonObject toJson() {
        JsonObject jsonObject = super.toJson();
        if (getValue() != null) {
            jsonObject.addProperty("value", getValue().name());
        }
        return jsonObject;
    }

    @Override
    public void setValue(T value) {
        if (value == null) return;
        super.setValue(value);
        this.index = value.ordinal();
    }

    public T[] getValues() {
        if (values == null && getValue() != null) {
            return (T[]) getValue().getDeclaringClass().getEnumConstants();
        }
        return values != null ? values : (T[]) new Enum[0];
    }

    public void setValues(T[] values) {
        this.values = values != null ? values : (T[]) new Enum[0];
    }

    public static class Builder<T extends Enum<?>> extends ConfigBuilder<T> {

        private T[] values;

        public Builder(String name) {
            super(name);
        }

        public Builder<T> setValues(T[] values) {
            this.values = values;
            return this;
        }

        @Override
        public EnumConfig<T> build() {
            EnumConfig<T> config = (EnumConfig<T>) super.build();

            if (values == null && config.getValue() != null) {
                values = (T[]) config.getValue().getDeclaringClass().getEnumConstants();
            }

            config.setValues(values);
            return config;
        }
    }
}
