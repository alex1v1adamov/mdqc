package example.config;

import com.yahoo.elide.core.utils.coerce.converters.ElideTypeConverter;
import com.yahoo.elide.core.utils.coerce.converters.Serde;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

@ElideTypeConverter(type = Point.class, name = "Point")
public class PointSerde implements Serde<String, Point> {
  @Override
  public Point deserialize(String val) {

      return new GeometryFactory().createPoint(new Coordinate(1, 2));
  }

  @Override
  public String serialize(Point val) {
    //        GeometrySerializer geometrySerializer = new GeometrySerializer();

    return "TEST Point STRING";
  }
}
