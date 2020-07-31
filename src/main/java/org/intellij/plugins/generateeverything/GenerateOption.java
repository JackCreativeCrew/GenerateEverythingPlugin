package org.intellij.plugins.generateeverything;

public enum GenerateOption {

    EMPTY_CONSTRUCTOR("emptyConstructor"),
    ALL_ARGS_CONSTRUCTOR("allArgsConstructor"),
    SUPER_OBJECT_CONSTRUCTOR("superObjectConstructor"),
    SUPER_ARGS_CONSTRUCTOR("superArgsConstructor"),
    ALL_ARGS_SUPER_CONSTRUCTOR("allArgsSuperConstructor"),
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
