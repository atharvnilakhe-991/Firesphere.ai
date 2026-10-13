package ai.firesphere.integration.adapter.storage;

import ai.firesphere.integration.api.storage.FileDeleteRequest;
import ai.firesphere.integration.api.storage.FileDeleteResult;
import ai.firesphere.integration.api.storage.FileDownloadRequest;
import ai.firesphere.integration.api.storage.FileDownloadResult;
import ai.firesphere.integration.api.storage.FileUploadRequest;
import ai.firesphere.integration.api.storage.FileUploadResult;
import ai.firesphere.integration.api.storage.StorageIntegration;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.storage.StorageProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing StorageIntegration, mediating between domain code and object storage engines.
 */
@Service
public class StorageIntegrationAdapter implements StorageIntegration {

    private final StorageProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public StorageIntegrationAdapter(
            StorageProvider provider,
            IntegrationProperties properties,
            IntegrationRetryExecutor retryExecutor,
            IdempotencyStore idempotencyStore,
            IntegrationLogger logger
    ) {
        this.provider = provider;
        this.properties = properties;
        this.retryExecutor = retryExecutor;
        this.idempotencyStore = idempotencyStore;
        this.logger = logger;
    }

    @Override
    public IntegrationResponse<FileUploadResult> uploadFile(IntegrationRequest<FileUploadRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<FileUploadResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), FileUploadResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<FileUploadResult> hit = cached.get();
                return IntegrationResponse.duplicate(
                        corrId,
                        provider.getProviderName(),
                        "Idempotent upload response replayed",
                        hit.data(),
                        0L
                );
            }
        }

        logger.logRequest(provider.getProviderName(), "uploadFile", request);

        IntegrationResponse<FileUploadResult> response = retryExecutor.executeWithRetry(
                "uploadFile",
                provider.getProviderName(),
                properties.getStorage().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        FileUploadResult result = provider.upload(request != null ? request.payload() : null);
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.fileId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("uploadFile", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<FileDownloadResult> downloadFile(IntegrationRequest<FileDownloadRequest> request) {
        String corrId = resolveCorrelationId(request);

        logger.logRequest(provider.getProviderName(), "downloadFile", request);

        IntegrationResponse<FileDownloadResult> response = retryExecutor.executeWithRetry(
                "downloadFile",
                provider.getProviderName(),
                properties.getStorage().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        FileDownloadResult result = provider.download(request != null ? request.payload() : null);
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.fileId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("downloadFile", response);

        return response;
    }

    @Override
    public IntegrationResponse<FileDeleteResult> deleteFile(IntegrationRequest<FileDeleteRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<FileDeleteResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), FileDeleteResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<FileDeleteResult> hit = cached.get();
                return IntegrationResponse.duplicate(
                        corrId,
                        provider.getProviderName(),
                        "Idempotent delete response replayed",
                        hit.data(),
                        0L
                );
            }
        }

        logger.logRequest(provider.getProviderName(), "deleteFile", request);

        IntegrationResponse<FileDeleteResult> response = retryExecutor.executeWithRetry(
                "deleteFile",
                provider.getProviderName(),
                properties.getStorage().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        FileDeleteResult result = provider.delete(request != null ? request.payload() : null);
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.fileId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("deleteFile", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    private <T> String resolveCorrelationId(IntegrationRequest<T> request) {
        if (request != null && request.correlationId() != null && !request.correlationId().isBlank()) {
            CorrelationContext.set(request.correlationId());
            return request.correlationId();
        }
        return CorrelationContext.getOrGenerate();
    }
}
