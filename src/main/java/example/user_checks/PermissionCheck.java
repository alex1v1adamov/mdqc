package example.user_checks;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.yahoo.elide.annotation.SecurityCheck;
import com.yahoo.elide.core.security.ChangeSpec;
import com.yahoo.elide.core.security.RequestScope;
import com.yahoo.elide.core.security.User;
import com.yahoo.elide.core.security.checks.OperationCheck;
import example.models.policy.Permission;
import example.models.policy.QPermission;
import example.models.policy.UserRole;
import example.repo.PermissionRepository;
import example.service.PredicateGeneratorService;
import io.vavr.collection.Stream;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

@Slf4j
@SecurityCheck("RSMD")
@Component
public class PermissionCheck extends OperationCheck<Object> {

    @Autowired
    PermissionRepository permissionRepository;
    @Autowired
    PredicateGeneratorService predicateGeneratorService;
    @Autowired
    EntityManager entityManager;

    @Override
    public boolean ok(final Object object, final RequestScope requestScope, final Optional<ChangeSpec> optional) {

         ChangeSpec changeSpec = optional.get();
        String entityName = changeSpec.getResource().getResourceType().getName();
        Object attributeName = changeSpec.getFieldName();
        User user = requestScope.getUser();
        UserRole userRole = UserRole.ADMIN;
        Iterable<Permission> thisEntityPermissions = permissionRepository.findAll(QPermission.permission.entity.name.eq(entityName));

        return checkPermissions(object, Stream.ofAll(thisEntityPermissions), userRole, attributeName);
    }

    private boolean checkPermissions(
            final Object object,
            final Stream<Permission> thisEntityPermissions,
            final UserRole userRole,
            final Object attributeName) {
        if (thisEntityPermissions.isEmpty()) {
            return false;
        }
        Stream<Permission> thisAttributePermissions = thisEntityPermissions.filter(x -> x.getAttribute() == null || x.getAttribute().getName().equals(attributeName));
        if (thisAttributePermissions.isEmpty()) {
            return false;
        }
        Stream<Permission> roleSatisfiedPermissions = thisAttributePermissions.filter(x -> x.getUserRoles().contains(userRole));
        if (roleSatisfiedPermissions.isEmpty()) {
            return false;
        }
        Stream<Permission> forAllPredicatePermissions = roleSatisfiedPermissions.filter(x -> x.getPredicateDefinition() == null);
        if (!forAllPredicatePermissions.isEmpty()) {
            return true;
        }
        Stream<Permission> permissions = roleSatisfiedPermissions.filter(x -> x.getPredicateDefinition() != null)
                .filter(x -> {
                    BooleanExpression booleanExpression = predicateGeneratorService.generatePredicate(x.getPredicateDefinition());
                    PathBuilder<?> entity = new PathBuilder<>(object.getClass(), "entity");
                    JPAQuery<?> query = new JPAQuery<>(entityManager);

                    Field field = null;
                    Object value = null;
                    try {
                        field = object.getClass().getDeclaredField("id");
                        field.setAccessible(true);
                        value = field.get(object);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }

                    BooleanExpression eqById = entity.getString("id").eq(value.toString());

                    List<?> exists = query
                            .from(entity)
                            .where(booleanExpression.and(eqById)).fetch();
                    return !exists.isEmpty();
                });

        if (permissions.isEmpty()) {
            return false;
        } else {
            log.info("Удовлетворяющие permissions: " + permissions.map(Permission::getId).toJavaList());
            return true;

        }
    }
}