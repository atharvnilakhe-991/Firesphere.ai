package com.firesphere.backend.payment.service;

import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import com.firesphere.backend.payment.dto.PaymentCallbackRequest;
import com.firesphere.backend.payment.entity.PaymentRecord;
import com.firesphere.backend.payment.entity.PaymentStatus;
import com.firesphere.backend.payment.repository.PaymentRepository;
import com.firesphere.backend.workflow.model.WorkflowAction;
import com.firesphere.backend.workflow.service.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final NocApplicationRepository nocRepository;
    private final WorkflowService workflowService;
    private final AuditLogger auditLogger;

    public PaymentService(PaymentRepository paymentRepository, 
                          NocApplicationRepository nocRepository, 
                          WorkflowService workflowService, 
                          AuditLogger auditLogger) {
        this.paymentRepository = paymentRepository;
        this.nocRepository = nocRepository;
        this.workflowService = workflowService;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public void initiatePayment(UUID applicationId, String idempotencyKey) {
        // Authoritative workflow transition (includes tenancy/authorization checks)
        workflowService.performTransition(applicationId, new com.firesphere.backend.workflow.dto.WorkflowActionRequest(WorkflowAction.INITIATE_PAYMENT, null), idempotencyKey);
        
        // Idempotent creation of payment record
        if (paymentRepository.findByApplicationIdAndStatusNot(applicationId, PaymentStatus.FAILED).isPresent()) {
            return; // Already initiated or completed
        }

        PaymentRecord record = new PaymentRecord();
        record.setApplicationId(applicationId);
        record.setOrganizationId(com.firesphere.backend.common.security.TenantContext.getCurrentOrganizationId());
        record.setAmount(100.0); // Mock amount
        record.setStatus(PaymentStatus.PENDING);
        record.setReferenceId(UUID.randomUUID().toString()); // Mock reference generation
        paymentRepository.save(record);
        
        auditLogger.log(AuditEventType.NOC_WORKFLOW_TRANSITION, applicationId, "Payment initiated, reference: " + record.getReferenceId());
    }

    @Transactional
    public void processCallback(PaymentCallbackRequest request) {
        PaymentRecord record = paymentRepository.findByReferenceId(request.referenceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment reference not found"));

        if (record.getStatus() == PaymentStatus.COMPLETED) {
            // Idempotent return
            return;
        }

        if (request.success()) {
            record.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(record);
            
            // Advance workflow
            workflowService.performSystemTransition(record.getApplicationId(), WorkflowAction.COMPLETE_PAYMENT, "Payment successful: " + request.transactionId());
            
            auditLogger.log(AuditEventType.NOC_WORKFLOW_TRANSITION, record.getApplicationId(), "Payment verified and completed");
        } else {
            record.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(record);
            auditLogger.log(AuditEventType.NOC_WORKFLOW_TRANSITION, record.getApplicationId(), "Payment failed");
        }
    }
}
