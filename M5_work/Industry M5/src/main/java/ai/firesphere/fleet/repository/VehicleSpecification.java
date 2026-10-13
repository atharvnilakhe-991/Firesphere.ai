package ai.firesphere.fleet.repository;

import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic criteria specifications for fleet apparatus with strict tenant isolation.
 */
public class VehicleSpecification {

    public static Specification<Vehicle> withFilters(
            UUID tenantId,
            VehicleStatus status,
            VehicleType vehicleType,
            UUID stationId,
            String search
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Mandatory tenant isolation
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (vehicleType != null) {
                predicates.add(cb.equal(root.get("vehicleType"), vehicleType));
            }
            if (stationId != null) {
                predicates.add(cb.equal(root.get("assignedStationId"), stationId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate regMatch = cb.like(cb.lower(root.get("registrationNumber")), pattern);
                Predicate callSignMatch = cb.like(cb.lower(root.get("callSign")), pattern);
                predicates.add(cb.or(regMatch, callSignMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
