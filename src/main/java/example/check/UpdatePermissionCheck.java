package example.check;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.yahoo.elide.annotation.SecurityCheck;
import com.yahoo.elide.core.security.ChangeSpec;
import com.yahoo.elide.core.security.RequestScope;
import com.yahoo.elide.core.security.checks.OperationCheck;
import example.models.policy.OperationType;
import example.models.policy.Permission;
import example.models.policy.QPermission;
import example.models.policy.UserRole;
import example.repo.PermissionRepository;
import example.service.generator.PredicateGeneratorService;
import io.vavr.collection.Stream;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

@Slf4j
@SecurityCheck("FGAS.UPDATE")
@Component
@RequiredArgsConstructor
public class UpdatePermissionCheck extends OperationCheck<Object> {

  private final PermissionRepository permissionRepository;
  private final PredicateGeneratorService predicateGeneratorService;
  private final EntityManager entityManager;

  @Override
  public boolean ok(
      final Object entityObject,
      final RequestScope requestScope,
      final Optional<ChangeSpec> changeSpecOptional) {

    ChangeSpec changeSpec = changeSpecOptional.get();
    String entityName = changeSpec.getResource().getResourceType().getName();
    String attributeName = changeSpec.getFieldName();
    UserRole userRole = UserRole.ADMIN; // TODO User user = requestScope.getUser();
    Iterable<Permission> thisEntityPermissions =
        permissionRepository.findAll(QPermission.permission.entity.name.eq(entityName).and(
                QPermission.permission.operationType.eq(OperationType.UPDATE)));

    return checkPermissions(
        entityObject, Stream.ofAll(thisEntityPermissions), userRole, attributeName);
  }

  private boolean checkPermissions(
      final Object object,
      final Stream<Permission> thisEntityPermissions,
      final UserRole userRole,
      final Object attributeName) {
    return !thisEntityPermissions.isEmpty()
        && thisEntityPermissions.exists(
            permission ->
                       matchesAttribute(permission, attributeName)
                    && matchesUserRole(permission, userRole)
                    && satisfiesPermissionConditions(object, permission));
  }

  private boolean matchesAttribute(Permission permission, Object attributeName) {
    return permission.getAttribute() == null
        || permission.getAttribute().getName().equals(attributeName);
  }

  private boolean matchesUserRole(Permission permission, UserRole userRole) {
    return permission.getUserRoles().contains(userRole);
  }

  private boolean satisfiesPermissionConditions(Object object, Permission permission) {
    // Разрешения без предиката удовлетворяют всегда
    if (permission.getPredicateDefinition() == null) {
      return true;
    }
    // Проверяем разрешения с предикатом
    boolean satisfied = hasAllowingPermission(object, permission);
    if (satisfied) {
      log.debug("Permission {} satisfied with predicate", permission.getId());
    }
    return satisfied;
  }

  private boolean hasAllowingPermission(Object entity, Permission permission) {
    BooleanExpression predicate =
        predicateGeneratorService.generatePredicate(permission.getPredicateDefinition());

    PathBuilder<Object> entityPath = new PathBuilder<>(entity.getClass(), "entity");
    String entityId = extractEntityId(entity);
    BooleanExpression idCondition = entityPath.getString("id").eq(entityId);

    try {
      List<?> results =
          new JPAQuery<>(entityManager).from(entityPath).where(predicate.and(idCondition)).fetch();
      return !results.isEmpty();
    } catch (Exception e) {
      throw new RuntimeException(
          MessageFormat.format(
              "Failed to check permission for entity: {0}", entity.getClass().getSimpleName()),
          e);
    }
  }

  /** Извлекает ID сущности через рефлексию */
  private String extractEntityId(Object entity) {
    Objects.requireNonNull(entity, "Entity cannot be null");

    // Пробуем геттер
    Method getId = ReflectionUtils.findMethod(entity.getClass(), "getId");
    if (getId != null) {
      Object idValue = ReflectionUtils.invokeMethod(getId, entity);
      return validateAndConvertId(idValue);
    }

    // Пробуем поле
    Field idField = ReflectionUtils.findField(entity.getClass(), "id");
    if (idField != null) {
      ReflectionUtils.makeAccessible(idField);
      Object idValue = ReflectionUtils.getField(idField, entity);
      return validateAndConvertId(idValue);
    }

    throw new IllegalArgumentException(
        MessageFormat.format(
            "Entity class {0} does not have ''id'' field or getter",
            entity.getClass().getSimpleName()));
  }

  private String validateAndConvertId(Object idValue) {
    if (idValue == null) {
      throw new IllegalArgumentException("Entity ID cannot be null");
    }
    return idValue instanceof String str ? str : idValue.toString();
  }
}
