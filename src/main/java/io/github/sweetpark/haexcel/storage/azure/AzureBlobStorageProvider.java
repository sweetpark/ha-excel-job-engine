package io.github.sweetpark.haexcel.storage.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageResource;
import io.github.sweetpark.haexcel.storage.StorageType;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;

/**
 * Microsoft Azure Blob Storage provider. Uploads stream directly from disk via {@code
 * uploadFromFile} and downloads open a lazy blob input stream - the file is never fully
 * materialized in the JVM heap.
 */
public class AzureBlobStorageProvider implements StorageProvider {

  private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageProvider.class);

  private final String containerName;
  private final BlobContainerClient containerClient;

  public AzureBlobStorageProvider(String containerName, String connectionString) {
    this.containerName = containerName;
    BlobServiceClient serviceClient =
        new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
    this.containerClient = serviceClient.getBlobContainerClient(containerName);
    if (!containerClient.exists()) {
      containerClient.create();
    }
    log.info("[AzureBlobStorage] Initialized container={}", containerName);
  }

  @Override
  public StorageType getType() {
    return StorageType.AZURE;
  }

  @Override
  public String storeFile(Path source, String key, String contentType) throws IOException {
    BlobClient blobClient = containerClient.getBlobClient(key);
    blobClient.uploadFromFile(source.toString(), true);
    if (contentType != null) {
      blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
    }
    log.info("[AzureBlobStorage] Stored blob: container={} key={}", containerName, key);
    return key;
  }

  @Override
  public StorageResource getResource(String key) throws IOException {
    BlobClient blobClient = containerClient.getBlobClient(key);
    if (!blobClient.exists()) {
      return null;
    }
    try {
      var properties = blobClient.getProperties();
      return new StorageResource(
          new InputStreamResource(blobClient.openInputStream()),
          key,
          properties.getContentType(),
          properties.getBlobSize());
    } catch (BlobStorageException e) {
      if (e.getStatusCode() == 404) {
        return null;
      }
      throw e;
    }
  }

  @Override
  public void delete(String key) throws IOException {
    containerClient.getBlobClient(key).deleteIfExists();
    log.info("[AzureBlobStorage] Deleted blob: key={}", key);
  }

  public String getContainerName() {
    return containerName;
  }
}
