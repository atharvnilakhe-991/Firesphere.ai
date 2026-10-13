package ai.firesphere.integration.provider.storage;

import ai.firesphere.integration.api.storage.FileDeleteRequest;
import ai.firesphere.integration.api.storage.FileDeleteResult;
import ai.firesphere.integration.api.storage.FileDownloadRequest;
import ai.firesphere.integration.api.storage.FileDownloadResult;
import ai.firesphere.integration.api.storage.FileUploadRequest;
import ai.firesphere.integration.api.storage.FileUploadResult;

/**
 * Low-level SPI for object storage providers (e.g. S3, MinIO, Azure Blob, Mock Storage).
 */
public interface StorageProvider {

    String getProviderName();

    FileUploadResult upload(FileUploadRequest request);

    FileDownloadResult download(FileDownloadRequest request);

    FileDeleteResult delete(FileDeleteRequest request);
}
