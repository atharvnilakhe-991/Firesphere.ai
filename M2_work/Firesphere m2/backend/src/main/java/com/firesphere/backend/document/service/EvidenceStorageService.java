package com.firesphere.backend.document.service;

import java.io.InputStream;

public interface EvidenceStorageService {
    String storeEvidence(String storageKey, String contentType, InputStream data);
    byte[] retrieveEvidence(String storageKey);
    boolean evidenceExists(String storageKey);
    void deleteEvidence(String storageKey);
}
