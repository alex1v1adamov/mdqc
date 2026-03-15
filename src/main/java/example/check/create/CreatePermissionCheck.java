package example.check.create;

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
import example.repo.PermissionRepository;
import example.service.generator.PredicateGeneratorService;
import io.vavr.collection.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
@Slf4j
@SecurityCheck("FGAS.CREATE")
@Component
@RequiredArgsConstructor
public class CreatePermissionCheck extends OperationCheck<Object> {

    private final PermissionRepository permissionRepository;
    private final PredicateGeneratorService predicateGeneratorService;
    private final CreatePermissionEvaluationService evaluationService;

    @Override
    public boolean ok(
            final Object entityObject,
            final RequestScope requestScope,
            final Optional<ChangeSpec> changeSpecOptional) {

        String entityName = entityObject.getClass().getName();
        UserRole userRole = UserRole.ADMIN; // TODO: получить из requestScope
        log.debug("Checking CREATE permission for entity: {}, userRole: {}", entityName, userRole);

        Iterable<Permission> thisEntityPermissions =
                permissionRepository.findAll(QPermission.permission.entity.name.eq(entityName)
                        .and(QPermission.permission.operationType.eq(OperationType.CREATE)));

        return checkCreatePermissions(entityObject, Stream.ofAll(thisEntityPermissions), userRole);
    }

    private boolean checkCreatePermissions(
            final Object entityObject,
            final Stream<Permission> thisEntityPermissions,
            final UserRole userRole) {
        if (thisEntityPermissions.isEmpty()) {
            log.debug("No CREATE permissions found for entity");
            return true;
        }
        return thisEntityPermissions.exists(permission ->
                matchesUserRole(permission, userRole)
                        && satisfiesCreatePermissionConditions(entityObject, permission));
    }

    private boolean matchesUserRole(Permission permission, UserRole userRole) {
        boolean matches = permission.getUserRoles().contains(userRole);
        if (!matches) {
            log.debug("Permission {} does not match user role {}", permission.getId(), userRole);
        }
        return matches;
    }

    private boolean satisfiesCreatePermissionConditions(Object entityObject, Permission permission) {
        if (permission.getPredicateDefinition() == null) {
            log.debug("Permission {} satisfied (no predicate)", permission.getId());
            return true;
        }

        boolean satisfied = evaluatePredicateInMemory(entityObject, permission);
        if (satisfied) {
            log.debug("Permission {} satisfied with predicate", permission.getId());
        } else {
            log.debug("Permission {} NOT satisfied with predicate", permission.getId());
        }
        return satisfied;
    }

    private boolean evaluatePredicateInMemory(Object entityObject, Permission permission) {
        try {
            BooleanExpression predicate =
                    predicateGeneratorService.generatePredicate(permission.getPredicateDefinition());

            log.debug("Generated predicate for permission {}: {}", permission.getId(), predicate);

            @SuppressWarnings("unchecked")
            Class<Object> entityClass = (Class<Object>) entityObject.getClass();
            PathBuilder<Object> entityPath = new PathBuilder<>(entityClass, "entity");

            // Используем evaluation service для проверки в отдельной транзакции
            return evaluationService.evaluatePermission(entityObject, predicate, entityPath);

        } catch (Exception e) {
            log.warn("Failed to evaluate create permission predicate for entity: {}, permission: {}",
                    entityObject.getClass().getSimpleName(), permission.getId(), e);
            return false;
        }
    }

    private String extractEntityId(Object entity) {
        try {
            var getIdMethod = entity.getClass().getMethod("getId");
            Object idValue = getIdMethod.invoke(entity);
            return idValue != null ? idValue.toString() : "null";
        } catch (NoSuchMethodException e) {
            try {
                var idField = entity.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                Object idValue = idField.get(entity);
                return idValue != null ? idValue.toString() : "null";
            } catch (Exception ex) {
                return "unknown";
            }
        } catch (Exception e) {
            return "error";
        }
    }
}