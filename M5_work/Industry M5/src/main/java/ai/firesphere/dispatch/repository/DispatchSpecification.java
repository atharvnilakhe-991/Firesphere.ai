package ai.firesphere.dispatch.repository;

import ai.firesphere.dispatch.model.DispatchOrder;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic JPA criteria specifications for Dispatch Orders with mandatory tenant isolation.
 */
public class DispatchSpecification {

    public static Specification<DispatchOrder> withFilters(
            UUID tenantId,
            DispatchStatus status,
            DispatchPriority priority,
            UUID incidentId,
            UUID stationId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Enforce strict tenant isolation on every query
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (incidentId != null) {
                predicates.add(cb.equal(root.get("incidentId"), incidentId));
            }
            if (stationId != null) {
                predicates.add(cb.equal(root.get("stationId"), stationId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
