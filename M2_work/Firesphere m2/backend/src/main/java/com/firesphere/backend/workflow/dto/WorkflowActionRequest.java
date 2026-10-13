package com.firesphere.backend.workflow.dto;

import com.firesphere.backend.workflow.model.WorkflowAction;
import jakarta.validation.constraints.NotNull;

public record WorkflowActionRequest(
        @NotNull WorkflowAction action,
        String reason
) {}
