package example;

import com.cosium.spring.data.jpa.entity.graph.domain2.DynamicEntityGraph;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import example.models.meta.MetaEnum;
import example.repo.MetaEntityRepository;
import example.service.MetaEntityValidationService;
import example.service.ValidationResult;
import io.vavr.collection.Stream;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SchemaValidator {

  private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);

  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private MetaEntityRepository metaEntityRepository;
  @Autowired private MetaEntityValidationService metaEntityValidationService;

  @PostConstruct
  @Transactional
  public void validateSchema() {
    Metamodel metamodel = entityManagerFactory.getMetamodel();
    List<MetaEntity> businessEntitiesMetaData =
        metaEntityRepository.findAll(
            null,
            DynamicEntityGraph.loading()
                .addPath(MetaEntity.Fields.attributes)
                .addPath(
                    MetaEntity.Fields.attributes
                        + "."
                        + MetaAttribute.Fields.metaEnum
                        + "."
                        + MetaEnum.Fields.values)
                .build());
    Stream<ValidationResult> resultList =
        Stream.ofAll(businessEntitiesMetaData)
            .map(me -> metaEntityValidationService.validate(me))
            .filter(x -> !x.isValid());
    if (!resultList.isEmpty()) {
      throw new IllegalStateException(
          resultList
              .map(ValidationResult::getErrors)
              .flatMap(Stream::ofAll)
              .peek(logger::error)
              .toJavaList()
              .toString());
    }
    logger.info("Schema validation correct");
  }
}
