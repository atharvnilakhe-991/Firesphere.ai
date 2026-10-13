package com.firesphere.backend;

import com.firesphere.backend.certificate.entity.NocCertificate;
import com.firesphere.backend.certificate.service.PdfGeneratorService;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PdfGeneratorServiceTest {

    @Test
    void testPdfContentContainsAuthoritativeInformation() throws Exception {
        PdfGeneratorService service = new PdfGeneratorService();

        NocApplication app = new NocApplication();
        app.setId(UUID.randomUUID());
        app.setApplicantId(UUID.randomUUID());
        app.setOrganizationId(UUID.randomUUID());
        app.setTitle("Test Title Premises");
        app.setStatus(NocStatus.APPROVED);

        NocCertificate cert = new NocCertificate();
        cert.setPublicCertificateId("CERT-12345678");
        cert.setIssuedAt(OffsetDateTime.now());
        cert.setStatus("ACTIVE");

        byte[] pdfBytes = service.generateCertificatePdf(app, cert);
        assertThat(pdfBytes).isNotEmpty();

        PdfReader reader = new PdfReader(pdfBytes);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);

        assertThat(text).contains("CERT-12345678");
        assertThat(text).contains(app.getId().toString());
        assertThat(text).contains(app.getApplicantId().toString());
        assertThat(text).contains(app.getOrganizationId().toString());
        assertThat(text).contains("Test Title Premises");
        assertThat(text).contains("APPROVED for a Fire NOC");
    }
}
