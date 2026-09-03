package io.github.sweetpark.haexcel.storage.gcp;

import com.google.cloud.NoCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageResource;
import io.github.sweetpark.haexcel.storage.StorageType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;

/**
 * Google Cloud Storage (GCS) provider. Uploads stream from disk via a resumable {@link
 * WriteChannel} and downloads open a lazy object read channel - the file is never fully
 * materialized in the JVM heap.
 */
public class GcpCloudStorageProvider implements StorageProvider {

  private static final Logger log = LoggerFactory.getLogger(GcpCloudStorageProvider.class);
  private static final int CHUNK_SIZE = 8 * 1024 * 1024;

  private final String bucketName;
  private final String projectId;
  private final Storage storage;

  public GcpCloudStorageProvider(String bucketName, String projectId, String host) {
    this.bucketName = bucketName;
    this.projectId = projectId;
    StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
    if (projectId != null && !projectId.isBlank()) {
      optionsBuilder.setProjectId(projectId);
    }
    if (host != null && !host.isBlank()) {
      // A host override means this points at a local emulator (e.g. fake-gcs-server in tests),
      // which does not require real Application Default Credentials.
      optionsBuilder.setHost(host).setCredentials(NoCredentials.getInstance());
    }
    this.storage = optionsBuilder.build().getService();
    log.info("[GcpCloudStorage] Initialized bucket={} projectId={}", bucketName, projectId);
  }

  public GcpCloudStorageProvider(String bucketName, String projectId) {
    this(bucketName, projectId, null);
  }

  @Override
  public StorageType getType() {
    return StorageType.GCP;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    BlobInfo blobInfo =
        BlobInfo.newBuilder(BlobId.of(bucketName, key)).setContentType(contentType).build();
    try (WriteChannel writer = storage.writer(blobInfo);
        FileChannel fileChannel = FileChannel.open(source, StandardOpenOption.READ)) {
      java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(CHUNK_SIZE);
      while (fileChannel.read(buffer) != -1) {
        buffer.flip();
        writer.write(buffer);
        buffer.clear();
      }
    }
    log.info("[GcpCloudStorage] Stored gs://{}/{}", bucketName, key);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    Blob blob = storage.get(BlobId.of(bucketName, key));
    if (blob == null || !blob.exists()) {
      return null;
    }
    InputStream in = Channels.newInputStream(blob.reader());
    return new StorageResource(
        new InputStreamResource(in), key, blob.getContentType(), blob.getSize());
  }

  @Override
  public void delete(String key) throws IOException {
    storage.delete(BlobId.of(bucketName, key));
    log.info("[GcpCloudStorage] Deleted gs://{}/{}", bucketName, key);
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getProjectId() {
    return projectId;
  }
}
