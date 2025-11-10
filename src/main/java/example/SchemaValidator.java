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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaValidator {

  private final MetaEntityRepository metaEntityRepository;
  private final MetaEntityValidationService metaEntityValidationService;

  @PostConstruct
  @Transactional
  public void validateSchema() {
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
            .map(metaEntityValidationService::validate)
            .filter(x -> !x.isValid());
    if (!resultList.isEmpty()) {
      throw new IllegalStateException(
          resultList
              .map(ValidationResult::getErrors)
              .flatMap(Stream::ofAll)
              .peek(log::error)
              .toJavaList()
              .toString());
    }
    log.info("Schema validation correct");
  }
}
