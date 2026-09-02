package io.github.sweetpark.haexcel.storage.local;

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

public class LocalDiskStorageProvider implements StorageProvider {

  private static final Logger log = LoggerFactory.getLogger(LocalDiskStorageProvider.class);

  private final Path rootDir;

  public LocalDiskStorageProvider(Path rootDir) {
    this.rootDir = rootDir;
    try {
      Files.createDirectories(rootDir);
    } catch (IOException e) {
      log.error("Failed to create local storage directory: {}", rootDir, e);
    }
  }

  @Override
  public StorageType getType() {
    return StorageType.LOCAL;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    Path target = rootDir.resolve(key).normalize();
    Path normSource = source.toAbsolutePath().normalize();
    if (!normSource.equals(target.toAbsolutePath().normalize())) {
      if (target.getParent() != null) {
        Files.createDirectories(target.getParent());
      }
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
    log.info("[LocalDiskStorage] Stored file at: {}", target);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    Path target = rootDir.resolve(key);
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
    Path target = rootDir.resolve(key);
    boolean deleted = Files.deleteIfExists(target);
    if (deleted) {
      log.info("[LocalDiskStorage] Deleted file: {}", target);
    }
  }
}
