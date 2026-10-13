package ai.firesphere.integration.controller;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.integration.api.government.CadStatusQueryRequest;
import ai.firesphere.integration.api.government.CadStatusQueryResult;
import ai.firesphere.integration.api.government.CadTransmitRequest;
import ai.firesphere.integration.api.government.CadTransmitResult;
import ai.firesphere.integration.api.government.ComplianceLookupRequest;
import ai.firesphere.integration.api.government.ComplianceLookupResult;
import ai.firesphere.integration.api.government.GovernmentServiceIntegration;
import ai.firesphere.integration.api.email.EmailIntegration;
import ai.firesphere.integration.api.email.EmailMessageRequest;
import ai.firesphere.integration.api.email.EmailMessageResult;
import ai.firesphere.integration.api.maps.GeocodingRequest;
import ai.firesphere.integration.api.maps.GeocodingResult;
import ai.firesphere.integration.api.maps.MapsIntegration;
import ai.firesphere.integration.api.maps.ReverseGeocodingRequest;
import ai.firesphere.integration.api.maps.ReverseGeocodingResult;
import ai.firesphere.integration.api.maps.RouteMatrixRequest;
import ai.firesphere.integration.api.maps.RouteMatrixResult;
import ai.firesphere.integration.api.payment.PaymentInitiateRequest;
import ai.firesphere.integration.api.payment.PaymentInitiateResult;
import ai.firesphere.integration.api.payment.PaymentIntegration;
import ai.firesphere.integration.api.payment.PaymentStatusResult;
import ai.firesphere.integration.api.payment.PaymentVerificationRequest;
import ai.firesphere.integration.api.payment.PaymentVerificationResult;
import ai.firesphere.integration.api.signature.DigitalSignatureIntegration;
import ai.firesphere.integration.api.signature.SignatureRequest;
import ai.firesphere.integration.api.signature.SignatureResult;
import ai.firesphere.integration.api.signature.SignatureVerificationRequest;
import ai.firesphere.integration.api.signature.SignatureVerificationResult;
import ai.firesphere.integration.api.sms.SmsIntegration;
import ai.firesphere.integration.api.sms.SmsMessageRequest;
import ai.firesphere.integration.api.sms.SmsMessageResult;
import ai.firesphere.integration.api.whatsapp.WhatsAppIntegration;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageRequest;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageResult;
import ai.firesphere.integration.core.idempotency.IdempotencyKey;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import ai.firesphere.integration.service.OperationalIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Unified REST controller for the External Integration Framework and operational integration services.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "External Integrations", description = "Enterprise External Integration Framework API")
public class IntegrationOperationsController {

    private final PaymentIntegration paymentIntegration;
    private final SmsIntegration smsIntegration;
    private final EmailIntegration emailIntegration;
    private final WhatsAppIntegration whatsAppIntegration;
    private final MapsIntegration mapsIntegration;
    private final DigitalSignatureIntegration signatureIntegration;
    private final GovernmentServiceIntegration governmentServiceIntegration;
    private final OperationalIntegrationService operationalService;
    private final ai.firesphere.integration.api.storage.StorageIntegration storageIntegration;

    public IntegrationOperationsController(
            PaymentIntegration paymentIntegration,
            SmsIntegration smsIntegration,
            EmailIntegration emailIntegration,
            WhatsAppIntegration whatsAppIntegration,
            MapsIntegration mapsIntegration,
            DigitalSignatureIntegration signatureIntegration,
            GovernmentServiceIntegration governmentServiceIntegration,
            OperationalIntegrationService operationalService,
            ai.firesphere.integration.api.storage.StorageIntegration storageIntegration
    ) {
        this.paymentIntegration = paymentIntegration;
        this.smsIntegration = smsIntegration;
        this.emailIntegration = emailIntegration;
        this.whatsAppIntegration = whatsAppIntegration;
        this.mapsIntegration = mapsIntegration;
        this.signatureIntegration = signatureIntegration;
        this.governmentServiceIntegration = governmentServiceIntegration;
        this.operationalService = operationalService;
        this.storageIntegration = storageIntegration;
    }

    // --- Payment Endpoints ---

    @PostMapping("/payment/initiate")
    @Operation(summary = "Initiate external payment transaction")
    public ResponseEntity<IntegrationResponse<PaymentInitiateResult>> initiatePayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentInitiateRequest request
    ) {
        IntegrationRequest<PaymentInitiateRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<PaymentInitiateResult> resp = paymentIntegration.initiatePayment(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @PostMapping("/payment/verify")
    @Operation(summary = "Verify external payment settlement")
    public ResponseEntity<IntegrationResponse<PaymentVerificationResult>> verifyPayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentVerificationRequest request
    ) {
        IntegrationRequest<PaymentVerificationRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<PaymentVerificationResult> resp = paymentIntegration.verifyPayment(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @GetMapping("/payment/status/{transactionId}")
    @Operation(summary = "Query external payment transaction status")
    public ResponseEntity<IntegrationResponse<PaymentStatusResult>> getPaymentStatus(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String transactionId
    ) {
        IntegrationRequest<String> req = wrap(correlationId, null, transactionId);
        IntegrationResponse<PaymentStatusResult> resp = paymentIntegration.checkStatus(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @PostMapping("/payment/webhook")
    @Operation(summary = "Ingest external payment gateway webhook notification")
    public ResponseEntity<IntegrationResponse<ai.firesphere.integration.api.payment.PaymentWebhookResult>> processPaymentWebhook(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @Valid @RequestBody ai.firesphere.integration.api.payment.PaymentWebhookRequest request
    ) {
        IntegrationRequest<ai.firesphere.integration.api.payment.PaymentWebhookRequest> req = wrap(correlationId, null, request);
        IntegrationResponse<ai.firesphere.integration.api.payment.PaymentWebhookResult> resp = paymentIntegration.processWebhook(req, signature);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(resp);
    }

    @PostMapping("/payment/reconcile/{transactionId}")
    @Operation(summary = "Reconcile uncertain or disconnected payment transaction")
    public ResponseEntity<IntegrationResponse<ai.firesphere.integration.api.payment.PaymentReconciliationResult>> reconcilePayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String transactionId
    ) {
        IntegrationRequest<String> req = wrap(correlationId, null, transactionId);
        IntegrationResponse<ai.firesphere.integration.api.payment.PaymentReconciliationResult> resp = paymentIntegration.reconcilePayment(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- SMS Endpoints ---

    @PostMapping("/sms/send")
    @Operation(summary = "Dispatch SMS message via provider")
    public ResponseEntity<IntegrationResponse<SmsMessageResult>> sendSms(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SmsMessageRequest request
    ) {
        IntegrationRequest<SmsMessageRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<SmsMessageResult> resp = smsIntegration.sendSms(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- Email Endpoints ---

    @PostMapping("/email/send")
    @Operation(summary = "Dispatch transactional email via provider")
    public ResponseEntity<IntegrationResponse<EmailMessageResult>> sendEmail(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EmailMessageRequest request
    ) {
        IntegrationRequest<EmailMessageRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<EmailMessageResult> resp = emailIntegration.sendEmail(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- WhatsApp Endpoints ---

    @PostMapping("/whatsapp/send")
    @Operation(summary = "Dispatch WhatsApp message via provider")
    public ResponseEntity<IntegrationResponse<WhatsAppMessageResult>> sendWhatsApp(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WhatsAppMessageRequest request
    ) {
        IntegrationRequest<WhatsAppMessageRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<WhatsAppMessageResult> resp = whatsAppIntegration.sendWhatsApp(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- Maps Endpoints ---

    @PostMapping("/maps/geocode")
    @Operation(summary = "Geocode textual address to coordinates")
    public ResponseEntity<IntegrationResponse<GeocodingResult>> geocode(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GeocodingRequest request
    ) {
        IntegrationRequest<GeocodingRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<GeocodingResult> resp = mapsIntegration.geocode(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @PostMapping("/maps/reverse-geocode")
    @Operation(summary = "Reverse geocode coordinates to address")
    public ResponseEntity<IntegrationResponse<ReverseGeocodingResult>> reverseGeocode(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReverseGeocodingRequest request
    ) {
        IntegrationRequest<ReverseGeocodingRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<ReverseGeocodingResult> resp = mapsIntegration.reverseGeocode(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @PostMapping("/maps/route")
    @Operation(summary = "Calculate driving / emergency response route")
    public ResponseEntity<IntegrationResponse<RouteMatrixResult>> calculateRoute(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RouteMatrixRequest request
    ) {
        IntegrationRequest<RouteMatrixRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<RouteMatrixResult> resp = mapsIntegration.calculateRoute(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- Digital Signature Endpoints ---

    @PostMapping("/signature/sign")
    @Operation(summary = "Sign document or certificate using PKI provider")
    public ResponseEntity<IntegrationResponse<SignatureResult>> signDocument(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SignatureRequest request
    ) {
        IntegrationRequest<SignatureRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<SignatureResult> resp = signatureIntegration.signDocument(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @PostMapping("/signature/verify")
    @Operation(summary = "Verify digital signature validity")
    public ResponseEntity<IntegrationResponse<SignatureVerificationResult>> verifySignature(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SignatureVerificationRequest request
    ) {
        IntegrationRequest<SignatureVerificationRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<SignatureVerificationResult> resp = signatureIntegration.verifySignature(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- Government & CAD Endpoints ---

    @PostMapping("/cad/transmit")
    @Operation(summary = "Transmit incident payload to external 112/911 CAD exchange")
    public ResponseEntity<IntegrationResponse<CadTransmitResult>> transmitCad(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CadTransmitRequest request
    ) {
        IntegrationRequest<CadTransmitRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<CadTransmitResult> resp = governmentServiceIntegration.transmitCadIncident(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @GetMapping("/cad/status/{externalCadId}")
    @Operation(summary = "Query external CAD incident status")
    public ResponseEntity<IntegrationResponse<CadStatusQueryResult>> queryCadStatus(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String externalCadId
    ) {
        IntegrationRequest<CadStatusQueryRequest> req = wrap(correlationId, null, new CadStatusQueryRequest(externalCadId));
        IntegrationResponse<CadStatusQueryResult> resp = governmentServiceIntegration.queryCadStatus(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @PostMapping("/cad/compliance")
    @Operation(summary = "Query government building safety compliance record")
    public ResponseEntity<IntegrationResponse<ComplianceLookupResult>> verifyCompliance(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ComplianceLookupRequest request
    ) {
        IntegrationRequest<ComplianceLookupRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<ComplianceLookupResult> resp = governmentServiceIntegration.verifyCompliance(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- Operational AI Inspection Endpoints ---

    @PostMapping("/ai-findings")
    @Operation(summary = "Ingest AI inspection finding for officer review")
    public ResponseEntity<AIInspectionResultProviderContract.AIFindingIngestResult> ingestAiFinding(
            @Valid @RequestBody AIInspectionResultProviderContract.AIFindingDTO finding
    ) {
        AIInspectionResultProviderContract.AIFindingIngestResult result = operationalService.ingestAIFinding(finding);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/ai-findings/{id}")
    @Operation(summary = "Retrieve AI inspection finding by ID")
    public ResponseEntity<AIInspectionResultProviderContract.AIFindingDTO> getAiFinding(@PathVariable UUID id) {
        AIInspectionResultProviderContract.AIFindingDTO finding = operationalService.getAIFinding(id);
        return ResponseEntity.ok(finding);
    }

    @PostMapping("/ai-findings/{id}/review")
    @Operation(summary = "Submit human officer decision on AI inspection finding")
    public ResponseEntity<AIInspectionResultProviderContract.AIFindingDTO> reviewAiFinding(
            @PathVariable UUID id,
            @Valid @RequestBody AIFindingReviewRequest request
    ) {
        AIInspectionResultProviderContract.AIFindingDTO updated = operationalService.reviewAIFinding(id, request);
        return ResponseEntity.ok(updated);
    }

    // --- Object Storage Endpoints ---

    @PostMapping("/storage/upload")
    @Operation(summary = "Upload evidence file or document to object storage")
    public ResponseEntity<IntegrationResponse<ai.firesphere.integration.api.storage.FileUploadResult>> uploadStorageFile(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ai.firesphere.integration.api.storage.FileUploadRequest request
    ) {
        IntegrationRequest<ai.firesphere.integration.api.storage.FileUploadRequest> req = wrap(correlationId, idempotencyKey, request);
        IntegrationResponse<ai.firesphere.integration.api.storage.FileUploadResult> resp = storageIntegration.uploadFile(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    @GetMapping("/storage/download/{fileId}")
    @Operation(summary = "Download file or evidence by file ID from object storage")
    public ResponseEntity<IntegrationResponse<ai.firesphere.integration.api.storage.FileDownloadResult>> downloadStorageFile(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String fileId
    ) {
        IntegrationRequest<ai.firesphere.integration.api.storage.FileDownloadRequest> req = wrap(correlationId, null, new ai.firesphere.integration.api.storage.FileDownloadRequest(fileId));
        IntegrationResponse<ai.firesphere.integration.api.storage.FileDownloadResult> resp = storageIntegration.downloadFile(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND).body(resp);
    }

    @DeleteMapping("/storage/{fileId}")
    @Operation(summary = "Delete file from object storage")
    public ResponseEntity<IntegrationResponse<ai.firesphere.integration.api.storage.FileDeleteResult>> deleteStorageFile(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String fileId
    ) {
        IntegrationRequest<ai.firesphere.integration.api.storage.FileDeleteRequest> req = wrap(correlationId, idempotencyKey, new ai.firesphere.integration.api.storage.FileDeleteRequest(fileId));
        IntegrationResponse<ai.firesphere.integration.api.storage.FileDeleteResult> resp = storageIntegration.deleteFile(req);
        return ResponseEntity.status(resp.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(resp);
    }

    // --- Asynchronous AI Job Endpoints ---

    @PostMapping("/ai-jobs/submit")
    @Operation(summary = "Submit evidence for asynchronous computer vision analysis")
    public ResponseEntity<AIInspectionResultProviderContract.AIJobStatusResponse> submitAiJob(
            @Valid @RequestBody AIInspectionResultProviderContract.AIJobSubmissionRequest request
    ) {
        AIInspectionResultProviderContract.AIJobStatusResponse resp = operationalService.submitAIJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }

    @GetMapping("/ai-jobs/{jobId}")
    @Operation(summary = "Retrieve asynchronous AI analysis job status")
    public ResponseEntity<AIInspectionResultProviderContract.AIJobStatusResponse> getAiJobStatus(
            @PathVariable String jobId
    ) {
        AIInspectionResultProviderContract.AIJobStatusResponse resp = operationalService.getAIJobStatus(jobId);
        return ResponseEntity.ok(resp);
    }

    private <T> IntegrationRequest<T> wrap(String correlationId, String idempotencyKey, T payload) {
        IdempotencyKey key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? IdempotencyKey.of(idempotencyKey)
                : null;
        return IntegrationRequest.of(correlationId, key, payload);
    }
}
