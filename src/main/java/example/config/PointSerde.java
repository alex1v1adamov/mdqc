package example.config;

import com.yahoo.elide.core.utils.coerce.converters.ElideTypeConverter;
import com.yahoo.elide.core.utils.coerce.converters.Serde;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

@ElideTypeConverter(type = Point.class, name = "Point")
public class PointSerde implements Serde<String, Point> {
  @Override
  public Point deserialize(String val) {
    try {
      WKTReader reader = new WKTReader();
      return (Point) reader.read(val);
    } catch (ParseException e) {
      throw new RuntimeException("Failed to parse WKT: " + val, e);
    }
  }

  @Override
  public String serialize(Point val) {
    return val.toText();
  }
}
