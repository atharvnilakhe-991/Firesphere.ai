package com.firesphere.backend.document.service;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

@Service
public class DummyDocumentStorageService implements DocumentStorageService {
    @Override
    public String storeDocument(String filename, String contentType, InputStream data) {
        return "mock-storage-ref/" + UUID.randomUUID() + "/" + filename;
    }
}
