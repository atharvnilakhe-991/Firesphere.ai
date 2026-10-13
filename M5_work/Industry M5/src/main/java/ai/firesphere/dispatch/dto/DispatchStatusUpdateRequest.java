package ai.firesphere.dispatch.dto;

import ai.firesphere.dispatch.model.DispatchStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DispatchStatusUpdateRequest(
        @NotNull(message = "New dispatch status is required")
        DispatchStatus status,

        @Size(max = 500, message = "Status notes cannot exceed 500 characters")
        String statusNotes
) {
    public String getEffectiveNotes() {
        return statusNotes != null ? statusNotes : "";
    }
}
