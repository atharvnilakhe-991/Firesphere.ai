package ai.firesphere.gis.foundation.model;

public enum LocationReferenceType {
    PROPERTY,
    INSPECTION,
    INCIDENT,
    FACILITY,
    OTHER;

    public static LocationReferenceType fromString(String val) {
        if (val == null || val.isBlank()) {
            return OTHER;
        }
        try {
            return LocationReferenceType.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
