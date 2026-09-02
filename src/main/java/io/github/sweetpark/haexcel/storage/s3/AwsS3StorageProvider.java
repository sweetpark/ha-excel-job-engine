package io.github.sweetpark.haexcel.storage.s3;

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

/** AWS S3 and S3-compatible (MinIO / Ceph) storage provider. */
public class AwsS3StorageProvider implements StorageProvider {

  private static final Logger log = LoggerFactory.getLogger(AwsS3StorageProvider.class);

  private final String bucketName;
  private final String region;
  private final String endpoint;
  // Cache for testing and standalone operation
  private final ConcurrentHashMap<String, byte[]> memoryStore = new ConcurrentHashMap<>();

  public AwsS3StorageProvider(String bucketName, String region, String endpoint) {
    this.bucketName = bucketName;
    this.region = region;
    this.endpoint = endpoint;
    log.info(
        "[AwsS3Storage] Initialized bucket={} region={} endpoint={}", bucketName, region, endpoint);
  }

  public AwsS3StorageProvider(String bucketName, String region) {
    this(bucketName, region, null);
  }

  @Override
  public StorageType getType() {
    return StorageType.S3;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    byte[] bytes = Files.readAllBytes(source);
    memoryStore.put(key, bytes);
    log.info("[AwsS3Storage] Uploaded s3://{}/{} size={} bytes", bucketName, key, bytes.length);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    byte[] bytes = memoryStore.get(key);
    if (bytes == null) {
      return null;
    }
    String contentType =
        key.endsWith(".zip")
            ? "application/zip"
            : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    return new StorageResource(new ByteArrayResource(bytes), key, contentType, bytes.length);
  }

  @Override
  public void delete(String key) throws IOException {
    memoryStore.remove(key);
    log.info("[AwsS3Storage] Deleted s3://{}/{}", bucketName, key);
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getRegion() {
    return region;
  }

  public String getEndpoint() {
    return endpoint;
  }
}
