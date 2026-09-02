package io.github.sweetpark.haexcel.storage;

import org.springframework.core.io.Resource;

public record StorageResource(
    Resource resource, String storageKey, String contentType, long contentLength) {}
