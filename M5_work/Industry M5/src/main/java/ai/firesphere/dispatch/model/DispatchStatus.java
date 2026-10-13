package ai.firesphere.dispatch.model;

/**
 * Explicit operational lifecycle statuses for Emergency Dispatch Orders.
 * Progression: CREATED -> DISPATCHED -> ACKNOWLEDGED -> EN_ROUTE -> ARRIVED -> COMPLETED
 * Terminal: COMPLETED, CANCELLED
 */
public enum DispatchStatus {
    CREATED,
    DISPATCHED,
    ACKNOWLEDGED,
    EN_ROUTE,
    ARRIVED,
    COMPLETED,
    CANCELLED
}
