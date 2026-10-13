package ai.firesphere.operations.officer.model;

public enum OfficerTaskType {
    INSPECTION,
    HAZARD_VERIFICATION,
    HYDRANT_SURVEY,
    EVACUATION_CHECK,
    OPERATIONAL_PATROL;

    public static OfficerTaskType fromString(String val) {
        if (val == null || val.isBlank()) {
            return INSPECTION;
        }
        try {
            return OfficerTaskType.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INSPECTION;
        }
    }
}
