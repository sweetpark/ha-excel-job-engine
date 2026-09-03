package io.github.sweetpark.haexcel.storage.ncp;

import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageResource;
import io.github.sweetpark.haexcel.storage.StorageType;
import io.github.sweetpark.haexcel.storage.s3.AwsS3StorageProvider;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Naver Cloud Platform (NCP) Object Storage provider. S3-compatible storage with default endpoint
 * kr.object.ncloudstorage.com. Delegates to {@link AwsS3StorageProvider} since NCP Object Storage
 * implements the S3 protocol; only the credentials and endpoint differ from AWS.
 */
public class NcpObjectStorageProvider implements StorageProvider, Closeable {

  private static final Logger log = LoggerFactory.getLogger(NcpObjectStorageProvider.class);
  private static final String DEFAULT_NCP_ENDPOINT = "https://kr.object.ncloudstorage.com";

  private final AwsS3StorageProvider delegate;

  public NcpObjectStorageProvider(
      String bucketName, String region, String endpoint, String accessKey, String secretKey) {
    String effectiveEndpoint =
        (endpoint != null && !endpoint.isBlank()) ? endpoint : DEFAULT_NCP_ENDPOINT;
    this.delegate =
        new AwsS3StorageProvider(bucketName, region, effectiveEndpoint, accessKey, secretKey);
    log.info(
        "[NcpObjectStorage] Initialized NCP Object Storage bucket={} endpoint={}",
        bucketName,
        effectiveEndpoint);
  }

  public NcpObjectStorageProvider(String bucketName, String region, String endpoint) {
    this(bucketName, region, endpoint, null, null);
  }

  public NcpObjectStorageProvider(String bucketName, String region) {
    this(bucketName, region, DEFAULT_NCP_ENDPOINT, null, null);
  }

  @Override
  public StorageType getType() {
    return StorageType.NCP;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    return delegate.storeFile(source, key, contentType);
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    return delegate.getResource(key);
  }

  @Override
  public void delete(String key) throws IOException {
    delegate.delete(key);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
