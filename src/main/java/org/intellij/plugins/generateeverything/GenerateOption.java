package org.intellij.plugins.generateeverything;

public enum GenerateOption {

    EMPTY_CONSTRUCTOR("emptyConstructor"),
    SUPER_CONSTRUCTOR("superConstructor"),
    ALL_ARGS_CONSTRUCTOR("allArgsConstructor"),
    GETTERS("getters"),
    SETTERS("setters"),
    TO_STRING("toString");

    private final String property;

    GenerateOption(final String property) {
        this.property = String.format("GenerateGenerator.%s", property);
    }

    public String getProperty() {
        return property;
    }
}
