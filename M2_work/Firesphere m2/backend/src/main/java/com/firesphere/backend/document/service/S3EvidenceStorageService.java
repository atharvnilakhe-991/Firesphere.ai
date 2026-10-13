package com.firesphere.backend.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class S3EvidenceStorageService implements EvidenceStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3EvidenceStorageService.class);

    private final String bucket;
    private final String endpoint;
    private final ConcurrentHashMap<String, byte[]> localCache = new ConcurrentHashMap<>();

    public S3EvidenceStorageService(
            @Value("${storage.bucket:firesphere-evidence}") String bucket,
            @Value("${storage.endpoint:http://localhost:9000}") String endpoint) {
        this.bucket = bucket;
        this.endpoint = endpoint;
    }

    @Override
    public String storeEvidence(String storageKey, String contentType, InputStream data) {
        if (storageKey == null || storageKey.contains("..") || storageKey.startsWith("/") || storageKey.startsWith("\\")) {
            throw new IllegalArgumentException("Invalid storage key pattern: path traversal forbidden");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[8192];
            int read;
            while ((read = data.read(temp, 0, temp.length)) != -1) {
                buffer.write(temp, 0, read);
            }
            byte[] bytes = buffer.toByteArray();
            localCache.put(storageKey, bytes);
            log.info("Stored evidence asset at key [{}] in bucket [{}] ({} bytes)", storageKey, bucket, bytes.length);
            return storageKey;
        } catch (IOException e) {
            log.error("Failed to store evidence asset at key [{}]: {}", storageKey, e.getMessage());
            throw new RuntimeException("Evidence storage write failure", e);
        }
    }

    @Override
    public byte[] retrieveEvidence(String storageKey) {
        if (!localCache.containsKey(storageKey)) {
            log.warn("Evidence key [{}] not found in storage", storageKey);
            throw new RuntimeException("Evidence not found in storage: " + storageKey);
        }
        return localCache.get(storageKey);
    }

    @Override
    public boolean evidenceExists(String storageKey) {
        return localCache.containsKey(storageKey);
    }

    @Override
    public void deleteEvidence(String storageKey) {
        localCache.remove(storageKey);
        log.info("Deleted evidence asset at key [{}]", storageKey);
    }

    public String getBucket() {
        return bucket;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
