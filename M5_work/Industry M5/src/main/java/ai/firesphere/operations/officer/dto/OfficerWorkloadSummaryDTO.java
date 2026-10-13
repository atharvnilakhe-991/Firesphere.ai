package ai.firesphere.operations.officer.dto;

import ai.firesphere.operations.officer.model.OfficerWorkloadSummary;

public record OfficerWorkloadSummaryDTO(
        String officerBadge,
        long totalAssignedTasks,
        long pendingTasks,
        long inProgressTasks,
        long completedTasks,
        long overdueTasks,
        long highPriorityTasks,
        String workloadCapacityStatus // OPTIMAL, HEAVY, OVERLOADED
) {
    public static OfficerWorkloadSummaryDTO fromDomain(OfficerWorkloadSummary summary, int maxCapacity) {
        if (summary == null) {
            return null;
        }
        long active = summary.pendingCount() + summary.inProgressCount();
        String capacityStatus;
        if (active >= maxCapacity) {
            capacityStatus = "OVERLOADED";
        } else if (active >= (maxCapacity * 0.7)) {
            capacityStatus = "HEAVY";
        } else {
            capacityStatus = "OPTIMAL";
        }

        return new OfficerWorkloadSummaryDTO(
                summary.officerBadge(),
                summary.totalAssigned(),
                summary.pendingCount(),
                summary.inProgressCount(),
                summary.completedCount(),
                summary.overdueCount(),
                summary.highPriorityCount(),
                capacityStatus
        );
    }
}
