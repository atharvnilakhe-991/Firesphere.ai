package ai.firesphere.operations.officer.model;

public enum OfficerTaskStatus {
    UNASSIGNED,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    ESCALATED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == ASSIGNED || this == IN_PROGRESS || this == ESCALATED;
    }
}
