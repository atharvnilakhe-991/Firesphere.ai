package com.firesphere.backend.inspection.controller;

import com.firesphere.backend.inspection.dto.EvidenceResponse;
import com.firesphere.backend.inspection.service.NocInspectionEvidenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/evidence")
public class InspectionEvidenceController {

    private final NocInspectionEvidenceService evidenceService;

    public InspectionEvidenceController(NocInspectionEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceResponse> uploadEvidence(
            @PathVariable UUID inspectionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "captureTimestamp", required = false) String captureTimestamp,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude) {

        EvidenceResponse response = evidenceService.uploadEvidence(
                inspectionId, file, captureTimestamp, latitude, longitude
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EvidenceResponse>> listEvidence(@PathVariable UUID inspectionId) {
        List<EvidenceResponse> response = evidenceService.listEvidenceForInspection(inspectionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{evidenceId}/file")
    public ResponseEntity<byte[]> getEvidenceFile(
            @PathVariable UUID inspectionId,
            @PathVariable UUID evidenceId) {
        NocInspectionEvidenceService.EvidenceContent content = evidenceService.getEvidenceContent(inspectionId, evidenceId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String safeFileName = content.fileName() != null ? content.fileName().replaceAll("[\\r\\n\"\\\\]", "_") : "evidence.jpg";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName + "\"")
                .body(content.data());
    }
}
