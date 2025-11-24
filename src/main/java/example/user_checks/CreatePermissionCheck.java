package example.user_checks;

import com.querydsl.collections.CollQuery;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.yahoo.elide.annotation.SecurityCheck;
import com.yahoo.elide.core.security.ChangeSpec;
import com.yahoo.elide.core.security.RequestScope;
import com.yahoo.elide.core.security.checks.OperationCheck;
import example.models.policy.OperationType;
import example.models.policy.Permission;
import example.models.policy.QPermission;
import example.models.policy.UserRole;
import example.models.vet.Clinic;
import example.models.vet.QClinic;
import example.repo.PermissionRepository;
import example.service.generator.PredicateGeneratorService;
import io.vavr.collection.Stream;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.querydsl.collections.CollQueryFactory.from;

@Slf4j
@SecurityCheck("FGAS.CREATE")
@Component
@RequiredArgsConstructor
public class CreatePermissionCheck extends OperationCheck<Object> {

  private final PermissionRepository permissionRepository;
  private final PredicateGeneratorService predicateGeneratorService;

  @Override
  public boolean ok(
      final Object entityObject,
      final RequestScope requestScope,
      final Optional<ChangeSpec> changeSpecOptional) {

     String entityName = entityObject.getClass().getName();
    UserRole userRole = UserRole.ADMIN; // TODO User user = requestScope.getUser();
    Iterable<Permission> thisEntityPermissions =
        permissionRepository.findAll(QPermission.permission.entity.name.eq(entityName).and(
                QPermission.permission.operationType.eq(OperationType.CREATE)));

    return checkCreatePermissions(
        entityObject, Stream.ofAll(thisEntityPermissions), userRole);
  }

  private boolean checkCreatePermissions(
      final Object object,
      final Stream<Permission> thisEntityPermissions,
      final UserRole userRole) {
    return !thisEntityPermissions.isEmpty()
        && thisEntityPermissions.exists(
            permission ->
                matchesUserRole(permission, userRole)
                    && satisfiesCreatePermissionConditions(object, permission));
  }

  private boolean matchesUserRole(Permission permission, UserRole userRole) {
    return permission.getUserRoles().contains(userRole);
  }

  private boolean satisfiesCreatePermissionConditions(Object object, Permission permission) {
    // Разрешения без предиката удовлетворяют всегда
    if (permission.getPredicateDefinition() == null) {
      return true;
    }
    // Проверяем разрешения с предикатом на новом объекте в памяти
    boolean satisfied = hasCreatePermission(object, permission);
    if (satisfied) {
      log.debug("Create permission {} satisfied with predicate", permission.getId());
    }
    return satisfied;
  }

    private boolean hasCreatePermission(Object entity, Permission permission) {
        try {
            BooleanExpression predicate =
                    predicateGeneratorService.generatePredicate(permission.getPredicateDefinition());

            PathBuilder<Object> entityPath = new PathBuilder<>(entity.getClass(), "entity");
            // Создаем запрос напрямую без factory
            long count = new CollQuery<Void>().from(entityPath, Collections.singletonList(entity)).select(entityPath)
                    .where(predicate)
                    .fetchCount();
            return count > 0;
        } catch (Exception e) {
            log.warn("Failed to check create permission for entity: {}",
                    entity.getClass().getSimpleName(), e);
            return false;
        }
    }
}


//List<Clinic> cats = Arrays.asList(new Clinic(), new Clinic());
//QClinic qclinic = QClinic.clinic;
//
//// Query the collection
//Long x = from(qclinic, cats) // from(Q-type, collection)
//        .where(qclinic.isOpen.eq(Boolean.TRUE))       // where clause using Q-type properties
//        .fetchCount();                      // execute the query
//    System.out.println(x);