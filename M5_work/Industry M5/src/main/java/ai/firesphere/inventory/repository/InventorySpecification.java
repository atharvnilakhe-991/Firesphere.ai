package ai.firesphere.inventory.repository;

import ai.firesphere.inventory.model.InventoryItem;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InventorySpecification {

    private InventorySpecification() {
    }

    public static Specification<InventoryItem> withFilters(
            UUID tenantId,
            String category,
            UUID stationId,
            Boolean isActive,
            Boolean lowStock,
            String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory tenant isolation
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            // 2. Category filter
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("category")), category.trim().toUpperCase()));
            }

            // 3. Station filter
            if (stationId != null) {
                predicates.add(cb.equal(root.get("stationId"), stationId));
            }

            // 4. Active state filter
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            // 5. Low-stock operational filter
            if (Boolean.TRUE.equals(lowStock)) {
                predicates.add(cb.lessThanOrEqualTo(root.get("quantityOnHand"), root.get("minimumThreshold")));
            }

            // 6. Case-insensitive search on name or SKU
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toUpperCase() + "%";
                Predicate nameLike = cb.like(cb.upper(root.get("name")), pattern);
                Predicate skuLike = cb.like(cb.upper(root.get("sku")), pattern);
                predicates.add(cb.or(nameLike, skuLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
