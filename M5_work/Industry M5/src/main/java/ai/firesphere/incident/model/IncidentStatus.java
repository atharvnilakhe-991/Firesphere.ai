package ai.firesphere.incident.model;

/**
 * Lifecycle states of an emergency incident.
 * Strict progression:
 * REPORTED -> VERIFIED -> DISPATCHED -> EN_ROUTE -> ON_SCENE -> UNDER_CONTROL -> RESOLVED -> CLOSED
 */
public enum IncidentStatus {
    REPORTED,
    VERIFIED,
    DISPATCHED,
    EN_ROUTE,
    ON_SCENE,
    UNDER_CONTROL,
    RESOLVED,
    CLOSED
}
