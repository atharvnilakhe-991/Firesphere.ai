package ai.firesphere.operations.officer.service;

import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.dto.*;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OfficerOperationsService {

    OfficerTaskResponseDTO createTask(OfficerTaskCreateRequest request);

    OfficerTaskResponseDTO getTask(UUID taskId);

    OfficerTaskResponseDTO getTaskByReference(String taskReference);

    List<OfficerTaskResponseDTO> getTasks(
            String officerBadge,
            OfficerTaskStatus status,
            DispatchPriority priority,
            Instant fromDate,
            Instant toDate
    );

    OfficerTaskResponseDTO updateTaskStatus(UUID taskId, OfficerTaskStatusUpdateRequest request);

    OfficerTaskResponseDTO updateTaskChecklist(UUID taskId, OfficerTaskChecklistUpdateRequest request);

    List<OfficerTaskResponseDTO> getOverdueTasks();

    OfficerWorkloadSummaryDTO getOfficerWorkload(String officerBadge);

    List<OperationalAlertDTO> evaluateOperationalAlerts();

    List<EscalationResultDTO> evaluateEscalations();

    EscalationResultDTO manualEscalateTask(UUID taskId, String reason);

    List<EscalationResultDTO> getEscalationHistory();
}
