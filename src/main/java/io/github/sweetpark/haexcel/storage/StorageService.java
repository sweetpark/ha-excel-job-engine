package io.github.sweetpark.haexcel.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/** High-level storage service facade wrapping the configured StorageProvider. */
public class StorageService {

  private static final Logger log = LoggerFactory.getLogger(StorageService.class);

  private final StorageProvider provider;

  public StorageService(StorageProvider provider) {
    this.provider = provider;
    log.info("[StorageService] Initialized with provider: {}", provider.getType());
  }

  public String storeFile(Path source, String key, String contentType) throws IOException {
    return provider.storeFile(source, key, contentType);
  }

  public Optional<Resource> getResource(String key) {
    try {
      StorageResource res = provider.getResource(key);
      if (res != null && res.resource() != null && res.resource().exists()) {
        return Optional.of(res.resource());
      }
    } catch (Exception e) {
      log.warn("[StorageService] Failed to get resource for key: {}", key, e);
    }
    return Optional.empty();
  }

  public void deleteFile(String key) {
    try {
      provider.delete(key);
    } catch (Exception e) {
      log.warn("[StorageService] Failed to delete file with key: {}", key, e);
    }
  }

  public StorageProvider getProvider() {
    return provider;
  }
}
