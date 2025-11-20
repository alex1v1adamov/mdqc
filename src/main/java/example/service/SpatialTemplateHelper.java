package example.service;

import com.querydsl.core.types.ConstantImpl;
import com.querydsl.core.types.dsl.*;
import java.text.MessageFormat;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class SpatialTemplateHelper {

  private static final String SCHEMA_NAME = "public";

  /**
   * Expression template расстояния между геометрией из пути и заданной геометрией Использует
   * PostGIS функцию ST_DistanceSphere для точных расчетов на сфере.
   *
   * @param geometryPath путь к геометрии в выражении QueryDSL (обычно из entity Q-класса)
   * @param geometry целевая геометрия для измерения расстояния
   * @return NumberTemplate для использования в QueryDSL predicate
   */
  public NumberTemplate<Double> distanceSphere(
      ComparablePath<? extends Geometry> geometryPath, Geometry geometry) {
    return Expressions.numberTemplate(
        Double.class,
        MessageFormat.format("{0}.ST_DistanceSphere({1}, {2})", SCHEMA_NAME, "{0}", "{1}"),
        geometryPath,
        ConstantImpl.create(geometry));
  }

  /**
   * Проверяет, находится ли геометрия полностью внутри заданной границы Использует PostGIS функцию
   * ST_Within
   *
   * @param geometryPath путь к геометрии в выражении QueryDSL
   * @param boundary геометрия-граница для проверки
   * @return BooleanExpression для использования в QueryDSL предикатах
   */
  public BooleanTemplate within(
      ComparablePath<? extends Geometry> geometryPath, Geometry boundary) {
    return Expressions.booleanTemplate(
        MessageFormat.format("{0}.ST_Within({1}, {2})", SCHEMA_NAME, "{0}", "{1}"),
        geometryPath,
        ConstantImpl.create(boundary));
  }

  /**
   * Проверяет пересечение двух геометрий Использует PostGIS функцию ST_Intersects
   *
   * @param geometry1 первая геометрия (выражение QueryDSL)
   * @param geometry2 вторая геометрия (константа)
   * @return BooleanExpression true если геометрии пересекаются
   */
  public BooleanExpression intersects(
      ComparablePath<? extends Geometry> geometry1, Geometry geometry2) {
    return Expressions.booleanTemplate(
            MessageFormat.format("{0}.ST_Intersects({1}, {2})", SCHEMA_NAME, "{0}", "{1}"),
            geometry1,
            ConstantImpl.create(geometry2))
        .eq(Boolean.TRUE);
  }

  /**
   * Проверяет, находится ли геометрия в пределах заданного расстояния Использует PostGIS функцию
   * ST_DWithin (более эффективна чем ST_Distance + сравнение)
   *
   * @param geometryPath путь к геометрии в выражении QueryDSL
   * @param geometry целевая геометрия для измерения расстояния
   * @param maxDistance максимальное расстояние в единицах CRS
   * @return BooleanExpression true если расстояние <= maxDistance
   */
  public BooleanExpression distanceWithin(
      ComparablePath<? extends Geometry> geometryPath, Geometry geometry, Double maxDistance) {
    return Expressions.booleanTemplate(
            MessageFormat.format(
                "{0}.ST_DWithin({1}, {2}, {3}, false)",
                SCHEMA_NAME, "{0}", "{1}", maxDistance.toString()),
            geometryPath,
            ConstantImpl.create(geometry))
        .eq(Boolean.TRUE);
  }
}
