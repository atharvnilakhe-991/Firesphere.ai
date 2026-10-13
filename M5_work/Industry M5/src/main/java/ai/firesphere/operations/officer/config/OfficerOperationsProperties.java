package ai.firesphere.operations.officer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable thresholds for officer operations, deadlines, alerts, and escalations.
 */
@Component
@ConfigurationProperties(prefix = "firesphere.officer-operations")
public class OfficerOperationsProperties {

    private long deadlineApproachingThresholdMinutes = 120;
    private long overdueEscalationThresholdMinutes = 60;
    private long highPriorityEscalationMinutes = 30;
    private String defaultEscalatedRole = "STATION_CAPTAIN";
    private int maxActiveTasksPerOfficer = 10;
    private boolean autoEscalationEnabled = true;

    public long getDeadlineApproachingThresholdMinutes() {
        return deadlineApproachingThresholdMinutes;
    }

    public void setDeadlineApproachingThresholdMinutes(long deadlineApproachingThresholdMinutes) {
        this.deadlineApproachingThresholdMinutes = deadlineApproachingThresholdMinutes;
    }

    public long getOverdueEscalationThresholdMinutes() {
        return overdueEscalationThresholdMinutes;
    }

    public void setOverdueEscalationThresholdMinutes(long overdueEscalationThresholdMinutes) {
        this.overdueEscalationThresholdMinutes = overdueEscalationThresholdMinutes;
    }

    public long getHighPriorityEscalationMinutes() {
        return highPriorityEscalationMinutes;
    }

    public void setHighPriorityEscalationMinutes(long highPriorityEscalationMinutes) {
        this.highPriorityEscalationMinutes = highPriorityEscalationMinutes;
    }

    public String getDefaultEscalatedRole() {
        return defaultEscalatedRole;
    }

    public void setDefaultEscalatedRole(String defaultEscalatedRole) {
        this.defaultEscalatedRole = defaultEscalatedRole;
    }

    public int getMaxActiveTasksPerOfficer() {
        return maxActiveTasksPerOfficer;
    }

    public void setMaxActiveTasksPerOfficer(int maxActiveTasksPerOfficer) {
        this.maxActiveTasksPerOfficer = maxActiveTasksPerOfficer;
    }

    public boolean isAutoEscalationEnabled() {
        return autoEscalationEnabled;
    }

    public void setAutoEscalationEnabled(boolean autoEscalationEnabled) {
        this.autoEscalationEnabled = autoEscalationEnabled;
    }
}
