package ai.firesphere.incident.dto;

import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentType;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record IncidentCreateRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        @NotNull(message = "Incident type is required")
        IncidentType incidentType,

        @NotNull(message = "Severity is required")
        IncidentSeverity severity,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and +90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and +90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and +180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and +180")
        Double longitude,

        @NotBlank(message = "Address is required")
        @Size(max = 500, message = "Address cannot exceed 500 characters")
        String address,

        @Size(max = 100, message = "Caller name cannot exceed 100 characters")
        String callerName,

        @Size(max = 30, message = "Caller phone cannot exceed 30 characters")
        String callerPhone,

        UUID assignedStationId,

        List<String> assignedResources
) {}
