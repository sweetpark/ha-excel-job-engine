package io.github.sweetpark.haexcel.storage.nas;

import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageResource;
import io.github.sweetpark.haexcel.storage.StorageType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

/**
 * Shared filesystem / NAS storage provider. Supports multi-node deployments sharing a
 * network-mounted disk (NFS/CIFS).
 */
public class NasStorageProvider implements StorageProvider {

  private static final Logger log = LoggerFactory.getLogger(NasStorageProvider.class);

  private final Path nasBasePath;

  public NasStorageProvider(Path nasBasePath) {
    this.nasBasePath = nasBasePath;
    try {
      Files.createDirectories(nasBasePath);
    } catch (IOException e) {
      log.error("Failed to initialize NAS storage directory: {}", nasBasePath, e);
    }
  }

  @Override
  public StorageType getType() {
    return StorageType.NAS;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    Path target = nasBasePath.resolve(key);
    if (target.getParent() != null) {
      Files.createDirectories(target.getParent());
    }

    // Perform safe copy then replace
    Path tempTarget = nasBasePath.resolve(key + ".tmp." + System.nanoTime());
    Files.copy(source, tempTarget, StandardCopyOption.REPLACE_EXISTING);
    try {
      Files.move(
          tempTarget, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      // Fallback for filesystems that do not support ATOMIC_MOVE
      Files.move(tempTarget, target, StandardCopyOption.REPLACE_EXISTING);
    }
    log.info("[NasStorage] Stored file at NAS path: {}", target);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    Path target = nasBasePath.resolve(key);
    if (!Files.exists(target)) {
      return null;
    }
    FileSystemResource resource = new FileSystemResource(target);
    String contentType = Files.probeContentType(target);
    if (contentType == null) {
      contentType =
          key.endsWith(".zip")
              ? "application/zip"
              : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    return new StorageResource(resource, key, contentType, Files.size(target));
  }

  @Override
  public void delete(String key) throws IOException {
    Path target = nasBasePath.resolve(key);
    Files.deleteIfExists(target);
    log.info("[NasStorage] Deleted NAS file: {}", target);
  }
}
