package com.firesphere.backend.certificate.service;

import com.firesphere.backend.certificate.entity.NocCertificate;
import com.firesphere.backend.noc.entity.NocApplication;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfGeneratorService {

    public byte[] generateCertificatePdf(NocApplication application, NocCertificate certificate) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("FireSphere.ai - Fire NOC Certificate"));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Certificate Number: " + certificate.getPublicCertificateId()));
            document.add(new Paragraph("Issue Date: " + certificate.getIssuedAt().toString()));
            document.add(new Paragraph("Status: " + certificate.getStatus()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("--- Applicant Information ---"));
            document.add(new Paragraph("Applicant ID: " + (application.getApplicantId() != null ? application.getApplicantId().toString() : "N/A")));
            document.add(new Paragraph("Organization ID: " + (application.getOrganizationId() != null ? application.getOrganizationId().toString() : (application.getOrgId() != null ? application.getOrgId().toString() : "N/A"))));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("--- Premises Information ---"));
            document.add(new Paragraph("Application ID: " + (application.getUuid() != null ? application.getUuid().toString() : (application.getId() != null ? application.getId().toString() : "N/A"))));
            document.add(new Paragraph("Title: " + (application.getTitle() != null ? application.getTitle() : "N/A")));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("--- Certificate Statement ---"));
            document.add(new Paragraph("This document certifies that the premises associated with the above Application ID has been APPROVED for a Fire NOC."));
            document.add(new Paragraph("This is an electronically generated certificate and is issued by the authoritative system."));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF certificate", e);
        }
    }
}
