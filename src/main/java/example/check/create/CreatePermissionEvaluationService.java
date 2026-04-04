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

/**
 * Сервис проверки прав на создание сущностей с временным сохранением и автоматическим откатом.
 *
 * <p><b>Принцип работы (кратко):</b>
 * <ol>
 *   <li>Временно сохраняет сущность в БД через {@code merge()} + {@code flush()}
 *       для получения сгенерированного ID.</li>
 *   <li>Выполняет запрос QueryDSL с переданным условием {@code predicate}
 *       и полученным ID.</li>
 *   <li>Возвращает {@code true}, если найдена существующая запись, иначе {@code false}.</li>
 *   <li><b>В любом случае откатывает транзакцию</b> — временные данные не фиксируются.</li>
 * </ol>
 *
 * <p>Использует {@code @Transactional(propagation = REQUIRES_NEW)} для изоляции
 * от вызывающей транзакции.
 */
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