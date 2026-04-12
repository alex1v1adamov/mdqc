package example.test.gpt.v2;

import com.yahoo.elide.core.Path;
import com.yahoo.elide.core.filter.Operator;
import com.yahoo.elide.core.filter.predicates.FilterPredicate;
import com.yahoo.elide.datastores.jpql.filter.JPQLPredicateGenerator;
import java.util.List;
import java.util.function.Function;
import org.locationtech.jts.geom.Geometry;

public class GeometryIntersectsFilter implements JPQLPredicateGenerator {

  private static final int SRID = 4326;

  @Override
  public String generate(FilterPredicate predicate, Function<Path, String> aliasGenerator) {
    if (predicate.getOperator() != Operator.IN) {
      throw new IllegalArgumentException("GeometryIntersectsFilter supports only Operator.IN");
    }

    List<Object> values = predicate.getValues();
    if (values == null || values.size() != 1) {
      throw new IllegalArgumentException(
          "Geometry intersects filter requires exactly one WKT argument");
    }

    String column = aliasGenerator.apply(predicate.getPath());
    String parameter = (predicate.getParameters().get(0).getPlaceholder());

    return String.format(
        "ST_Intersects(%s, ST_GeomFromText('%s', %s)) = true", column, parameter, SRID);
  }
}
