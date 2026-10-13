package com.firesphere.backend.inspection.dto;

public record UpdateInspectionRequest(
        String remarks,
        String location
) {}
