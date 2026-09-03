package io.github.sweetpark.haexcel.storage.s3;

import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageResource;
import io.github.sweetpark.haexcel.storage.StorageType;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 and S3-compatible (MinIO / NCP Object Storage) storage provider. Uploads/downloads stream
 * directly to/from disk via the AWS SDK v2 client - the file is never fully materialized in the JVM
 * heap.
 */
public class AwsS3StorageProvider implements StorageProvider, Closeable {

  private static final Logger log = LoggerFactory.getLogger(AwsS3StorageProvider.class);

  private final String bucketName;
  private final String region;
  private final String endpoint;
  private final S3Client s3Client;

  public AwsS3StorageProvider(
      String bucketName, String region, String endpoint, String accessKey, String secretKey) {
    this.bucketName = bucketName;
    this.region = region;
    this.endpoint = endpoint;
    this.s3Client = buildClient(region, endpoint, accessKey, secretKey);
    log.info(
        "[AwsS3Storage] Initialized bucket={} region={} endpoint={}", bucketName, region, endpoint);
  }

  public AwsS3StorageProvider(String bucketName, String region, String endpoint) {
    this(bucketName, region, endpoint, null, null);
  }

  public AwsS3StorageProvider(String bucketName, String region) {
    this(bucketName, region, null, null, null);
  }

  private static S3Client buildClient(
      String region, String endpoint, String accessKey, String secretKey) {
    S3ClientBuilder builder =
        S3Client.builder().region(Region.of(region != null ? region : "ap-northeast-2"));

    if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
      AwsCredentialsProvider credentials =
          StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
      builder.credentialsProvider(credentials);
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    if (endpoint != null && !endpoint.isBlank()) {
      builder
          .endpointOverride(URI.create(endpoint))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }

    return builder.build();
  }

  @Override
  public StorageType getType() {
    return StorageType.S3;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    PutObjectRequest request =
        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build();
    s3Client.putObject(request, RequestBody.fromFile(source));
    log.info("[AwsS3Storage] Uploaded s3://{}/{}", bucketName, key);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
    try {
      var responseStream = s3Client.getObject(request);
      long contentLength =
          responseStream.response().contentLength() != null
              ? responseStream.response().contentLength()
              : -1;
      String contentType = responseStream.response().contentType();
      return new StorageResource(
          new InputStreamResource(responseStream), key, contentType, contentLength);
    } catch (NoSuchKeyException e) {
      return null;
    }
  }

  @Override
  public void delete(String key) throws IOException {
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    log.info("[AwsS3Storage] Deleted s3://{}/{}", bucketName, key);
  }

  @Override
  public void close() {
    s3Client.close();
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
