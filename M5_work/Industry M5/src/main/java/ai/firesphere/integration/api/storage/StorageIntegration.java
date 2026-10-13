package ai.firesphere.integration.api.storage;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for Object Storage operations (evidence photos, inspection documents, reports).
 */
public interface StorageIntegration {

    IntegrationResponse<FileUploadResult> uploadFile(IntegrationRequest<FileUploadRequest> request);

    IntegrationResponse<FileDownloadResult> downloadFile(IntegrationRequest<FileDownloadRequest> request);

    IntegrationResponse<FileDeleteResult> deleteFile(IntegrationRequest<FileDeleteRequest> request);
}
