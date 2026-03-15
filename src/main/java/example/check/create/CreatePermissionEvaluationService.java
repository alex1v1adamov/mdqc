package example.check.create;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;

@Component
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CreatePermissionEvaluationService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public boolean evaluatePermission(Object entityObject, 
                                      BooleanExpression predicate,
                                      PathBuilder<Object> entityPath) {
        try {
            Object merge = entityManager.merge(entityObject);
            entityManager.flush();
            
            String entityId = extractEntityId(merge);
            BooleanExpression idCondition = entityPath.getString("id").eq(entityId);
            
            List<?> results = new JPAQuery<>(entityManager)
                    .from(entityPath)
                    .where(predicate.and(idCondition))
                    .fetch();

            return !results.isEmpty();
        } finally {
            // Принудительно откатываем транзакцию
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
    
    private String extractEntityId(Object entity) {
        try {
            var getIdMethod = entity.getClass().getMethod("getId");
            Object idValue = getIdMethod.invoke(entity);
            return idValue != null ? idValue.toString() : "null";
        } catch (Exception e) {
            return "unknown";
        }
    }
}