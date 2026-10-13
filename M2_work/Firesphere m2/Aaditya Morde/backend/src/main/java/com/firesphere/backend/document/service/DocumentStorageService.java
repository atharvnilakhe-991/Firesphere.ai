package com.firesphere.backend.document.service;

import java.io.InputStream;

public interface DocumentStorageService {
    String storeDocument(String filename, String contentType, InputStream data);
}
