package example.controllers;

import com.querydsl.jpa.impl.JPAQuery;
import example.models.QSite;
import example.models.Site;
import example.repo.SiteRepository;
import example.service.SpatialTemplateHelper;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

  private final SpatialTemplateHelper spatialHelper;
  private final EntityManager entityManager;
  private final SiteRepository siteRepository;

  @GetMapping("/within-radius")
  public List<Site> getSitesWithinRadius(
      @RequestParam double lng, @RequestParam double lat, @RequestParam double radius) {

    Point center = new GeometryFactory().createPoint(new Coordinate(lng, lat));

    var predicate =
        spatialHelper.distanceWithin(QSite.site.geometry, center, radius).eq(Boolean.TRUE);

    siteRepository.findAll(predicate);

    return new JPAQuery<Site>(entityManager)
        .select(QSite.site)
        .from(QSite.site)
        .where(predicate)
        .fetch();
  }
}
