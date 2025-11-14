package example.models.meta;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum BasicTypeCategory {
    TEXT(BasicType.STRING),
    LOGICAL(BasicType.BOOLEAN),
    NUMERIC(BasicType.INTEGER, BasicType.DOUBLE),
    TEMPORAL(BasicType.OFFSET_DATE_TIME),
    SPATIAL(BasicType.POINT),
    ENUMERATION(BasicType.ENUM),

    // Комбинированные категории
    COMPARABLE(BasicType.STRING, BasicType.BOOLEAN, BasicType.INTEGER,
            BasicType.DOUBLE, BasicType.OFFSET_DATE_TIME, BasicType.ENUM),
    ORDERED(BasicType.INTEGER, BasicType.DOUBLE, BasicType.OFFSET_DATE_TIME),
    ALL(BasicType.values()),
    NONE();

    private final Set<BasicType> basicTypes;

    BasicTypeCategory(BasicType... basicTypes) {
        this.basicTypes = basicTypes.length > 0
                ? EnumSet.copyOf(Arrays.asList(basicTypes))
                : EnumSet.noneOf(BasicType.class);
    }

    // остальные методы остаются без изменений
    public Set<BasicType> getBasicTypes() {
        return EnumSet.copyOf(basicTypes);
    }

    public boolean contains(BasicType basicType) {
        return basicTypes.contains(basicType);
    }

    public static Set<BasicTypeCategory> getCategoriesForType(BasicType basicType) {
        return Arrays.stream(BasicTypeCategory.values())
                .filter(category -> category.contains(basicType))
                .collect(Collectors.toSet());
    }
}