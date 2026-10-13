package com.firesphere.backend.workflow.dto;

import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.workflow.model.WorkflowAction;

import java.util.UUID;

public record WorkflowTransitionResponse(
        UUID applicationId,
        NocStatus previousState,
        WorkflowAction action,
        NocStatus currentState
) {}
