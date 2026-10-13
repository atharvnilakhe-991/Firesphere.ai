package ai.firesphere.operations.officer.dto;

import java.util.Collections;
import java.util.List;

/**
 * Request payload to update checklist completion and field notes for an operational task or inspection.
 */
public record OfficerTaskChecklistUpdateRequest(
        List<String> completedChecklistItems,
        String inspectionNotes
) {
    public OfficerTaskChecklistUpdateRequest {
        completedChecklistItems = completedChecklistItems != null ? Collections.unmodifiableList(completedChecklistItems) : List.of();
    }
}
