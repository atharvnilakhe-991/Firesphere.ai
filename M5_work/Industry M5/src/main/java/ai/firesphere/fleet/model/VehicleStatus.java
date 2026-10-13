package ai.firesphere.fleet.model;

/**
 * Authoritative operational lifecycle statuses for fleet vehicles and emergency apparatus.
 */
public enum VehicleStatus {
    AVAILABLE,
    ASSIGNED,
    DISPATCHED,
    EN_ROUTE,
    ON_SCENE,
    MAINTENANCE,
    OUT_OF_SERVICE
}
