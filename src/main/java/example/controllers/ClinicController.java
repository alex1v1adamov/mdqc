package example.controllers;

import com.querydsl.jpa.impl.JPAQuery;
import example.models.QClinic;
import example.models.Clinic;
import example.repo.ClinicRepository;
import example.service.SpatialTemplateHelper;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clinics")
@RequiredArgsConstructor
public class ClinicController {

  private final SpatialTemplateHelper spatialHelper;
  private final EntityManager entityManager;
  private final ClinicRepository clinicRepository;

  @GetMapping("/within-radius")
  public List<Clinic> getClinicsWithinRadius(
      @RequestParam double lng, @RequestParam double lat, @RequestParam double radius) {

    Point center = new GeometryFactory().createPoint(new Coordinate(lng, lat));

    var predicate =
        spatialHelper.distanceWithin(QClinic.clinic.geometry, center, radius).eq(Boolean.TRUE);

    clinicRepository.findAll(predicate);

    return new JPAQuery<Clinic>(entityManager)
        .select(QClinic.clinic)
        .from(QClinic.clinic)
        .where(predicate)
        .fetch();
  }
}
