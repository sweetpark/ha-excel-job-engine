package io.github.sweetpark.haexcel.storage.gcp;

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

/** Google Cloud Storage (GCS) provider. */
public class GcpCloudStorageProvider implements StorageProvider {

  private static final Logger log = LoggerFactory.getLogger(GcpCloudStorageProvider.class);

  private final String bucketName;
  private final String projectId;
  private final ConcurrentHashMap<String, byte[]> objectStore = new ConcurrentHashMap<>();

  public GcpCloudStorageProvider(String bucketName, String projectId) {
    this.bucketName = bucketName;
    this.projectId = projectId;
    log.info("[GcpCloudStorage] Initialized bucket={} projectId={}", bucketName, projectId);
  }

  @Override
  public StorageType getType() {
    return StorageType.GCP;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    byte[] data = Files.readAllBytes(source);
    objectStore.put(key, data);
    log.info("[GcpCloudStorage] Stored gs://{}/{} size={}", bucketName, key, data.length);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    byte[] data = objectStore.get(key);
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
    objectStore.remove(key);
    log.info("[GcpCloudStorage] Deleted gs://{}/{}", bucketName, key);
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getProjectId() {
    return projectId;
  }
}
