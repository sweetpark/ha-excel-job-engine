package io.github.sweetpark.haexcel.storage.azure;

import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageResource;
import io.github.sweetpark.haexcel.storage.StorageType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;

/** Microsoft Azure Blob Storage provider. */
public class AzureBlobStorageProvider implements StorageProvider {

  private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageProvider.class);

  private final String containerName;
  private final String connectionString;
  private final ConcurrentHashMap<String, byte[]> blobStore = new ConcurrentHashMap<>();

  public AzureBlobStorageProvider(String containerName, String connectionString) {
    this.containerName = containerName;
    this.connectionString = connectionString;
    log.info("[AzureBlobStorage] Initialized container={}", containerName);
  }

  @Override
  public StorageType getType() {
    return StorageType.AZURE;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    byte[] data = Files.readAllBytes(source);
    blobStore.put(key, data);
    log.info(
        "[AzureBlobStorage] Stored blob: container={} key={} size={}",
        containerName,
        key,
        data.length);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    byte[] data = blobStore.get(key);
    if (data == null) {
      return null;
    }
    String contentType =
        key.endsWith(".zip")
            ? "application/zip"
            : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    return new StorageResource(new ByteArrayResource(data), key, contentType, data.length);
  }

  @Override
  public void delete(String key) throws IOException {
    blobStore.remove(key);
    log.info("[AzureBlobStorage] Deleted blob: key={}", key);
  }

  public String getContainerName() {
    return containerName;
  }
}
