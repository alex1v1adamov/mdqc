package example.models.policy;


import com.yahoo.elide.annotation.Include;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import example.models.predicate.PredicateDefinition;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Include
@Entity
@Table(name = "permission", schema = "policy")
@Getter
@Setter
public class Permission {

    @Enumerated(EnumType.STRING)
    @ElementCollection
    @CollectionTable(
            name = "permission_user_roles",
            schema = "policy",
            joinColumns = @JoinColumn(name = "permission_id")
    )
    @Column(name = "user_role")
    Set<UserRole> userRoles = new HashSet<>();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    MetaEntity entity;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_id", nullable = false)
    MetaAttribute attribute;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predicate_id", nullable = true)
    PredicateDefinition predicateDefinition;
    /**
     * Уникальный идентификатор сущности
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

}