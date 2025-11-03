package example.user_checks;

import com.yahoo.elide.annotation.SecurityCheck;
import com.yahoo.elide.core.security.ChangeSpec;
import com.yahoo.elide.core.security.RequestScope;
import com.yahoo.elide.core.security.User;
import com.yahoo.elide.core.security.checks.OperationCheck;
import example.models.policy.Permission;
import example.models.policy.QPermission;
import example.models.policy.UserRole;
import example.repo.PermissionRepository;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;


@SecurityCheck("RSMD")
@Component
public class PermissionCheck extends OperationCheck<Object> {

    @Autowired
    PermissionRepository permissionRepository;

    @Override
    public boolean ok(final Object object, final RequestScope requestScope, final Optional<ChangeSpec> optional) {

        ChangeSpec changeSpec = optional.get();
        String entityName = changeSpec.getResource().getResourceType().getSimpleName();
        Object attributeName = changeSpec.getFieldName();
        User user = requestScope.getUser();
        UserRole userRole = UserRole.USER;
        Iterable<Permission> permissions = permissionRepository.findAll(QPermission.permission.entity.name.eq(object.getClass().getName()));

        return checkPermissions(permissions, entityName, userRole, attributeName);
    }

    private boolean checkPermissions(
            final Iterable<Permission> permissions,
            final String entityName,
            final UserRole userRole,
            final Object attributeName) {

        // Ищем entity permission
        Option<Permission> entityPermission = Stream.ofAll(permissions)
                .find(permission ->
                        Try.of(() -> permission.getEntity().getName().equals(entityName)).getOrElse(false)
                );
        // 1. Если есть entity permission
        if (entityPermission.isDefined()) {
            // 1.1 Но нет правильной роли - возвращаем false
            if (entityPermission.get().getUserRoles() == null ||
                    !entityPermission.get().getUserRoles().contains(userRole)) {
                return false;
            }

            // 1.2 Есть правильная роль - проверяем attribute permission
            Option<Permission> attributePermission = Stream.ofAll(permissions)
                    .find(permission ->
                            Try.of(() -> permission.getAttribute().getName().equals(attributeName)).getOrElse(false)
                    );
            // 2.1 Если есть attribute permission, но нет правильной роли - false
            if (attributePermission.isDefined() &&
                    (attributePermission.get().getUserRoles() == null ||
                            !attributePermission.get().getUserRoles().contains(userRole))) {
                return false;
            }
            // 2.2 Если есть attribute permission с правильной ролью - true
            // 2.3 Если нет attribute permission - true
            return true;
        }
        // 3. Если нет entity permission - проверяем только attribute permission
        Option<Permission> attributePermission = Stream.ofAll(permissions)
                .find(permission ->
                        Try.of(() -> permission.getAttribute().getName().equals(attributeName)).getOrElse(false)
                );
        // 3.1 Если есть attribute permission, но нет правильной роли - false
        if (attributePermission.isDefined() &&
                (attributePermission.get().getUserRoles() == null ||
                        !attributePermission.get().getUserRoles().contains(userRole))) {
            return false;
        }
        // 3.2 Если есть attribute permission с правильной ролью - true
        // 3.3 Если нет attribute permission - true
        return true;
    }
}