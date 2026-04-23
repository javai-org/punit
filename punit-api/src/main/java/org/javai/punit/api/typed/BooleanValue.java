package org.javai.punit.api.typed;

/** Boolean scalar factor value. */
public record BooleanValue(boolean value) implements FactorValue {

    @Override
    public String canonical() {
        return value ? "true" : "false";
    }

    @Override
    public Object yamlValue() {
        return value;
    }
}
