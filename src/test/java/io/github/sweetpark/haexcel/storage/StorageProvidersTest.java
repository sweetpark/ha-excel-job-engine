package io.github.sweetpark.haexcel.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sweetpark.haexcel.storage.azure.AzureBlobStorageProvider;
import io.github.sweetpark.haexcel.storage.gcp.GcpCloudStorageProvider;
import io.github.sweetpark.haexcel.storage.local.LocalDiskStorageProvider;
import io.github.sweetpark.haexcel.storage.nas.NasStorageProvider;
import io.github.sweetpark.haexcel.storage.ncp.NcpObjectStorageProvider;
import io.github.sweetpark.haexcel.storage.s3.AwsS3StorageProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageProvidersTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("LocalDiskStorageProvider correctly stores, loads, and deletes files")
  void testLocalStorage() throws Exception {
    Path root = tempDir.resolve("local-store");
    LocalDiskStorageProvider provider = new LocalDiskStorageProvider(root);
    assertEquals(StorageType.LOCAL, provider.getType());

    Path sample = tempDir.resolve("sample.xlsx");
    Files.writeString(sample, "hello excel");

    String key =
        provider.storeFile(
            sample,
            "sample.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertEquals("sample.xlsx", key);

    StorageResource resource = provider.getResource("sample.xlsx");
    assertNotNull(resource);
    assertTrue(resource.resource().exists());
    assertEquals(11, resource.contentLength());

    provider.delete("sample.xlsx");
    assertNull(provider.getResource("sample.xlsx"));
  }

  @Test
  @DisplayName("NasStorageProvider supports shared filesystem operations")
  void testNasStorage() throws Exception {
    Path nasRoot = tempDir.resolve("nas-store");
    NasStorageProvider provider = new NasStorageProvider(nasRoot);
    assertEquals(StorageType.NAS, provider.getType());

    Path sample = tempDir.resolve("nas-sample.xlsx");
    Files.writeString(sample, "nas content");

    provider.storeFile(
        sample,
        "nas-sample.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    StorageResource res = provider.getResource("nas-sample.xlsx");
    assertNotNull(res);
    assertTrue(res.resource().exists());

    provider.delete("nas-sample.xlsx");
    assertNull(provider.getResource("nas-sample.xlsx"));
  }

  @Test
  @DisplayName("Cloud storage providers (S3, NCP, Azure, GCP) store and retrieve properly")
  void testCloudStorageProviders() throws Exception {
    Path sample = tempDir.resolve("cloud-sample.xlsx");
    Files.writeString(sample, "cloud test data");

    // AWS S3
    AwsS3StorageProvider s3 = new AwsS3StorageProvider("test-bucket", "ap-northeast-2");
    assertEquals(StorageType.S3, s3.getType());
    s3.storeFile(
        sample,
        "s3-test.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertNotNull(s3.getResource("s3-test.xlsx"));
    s3.delete("s3-test.xlsx");
    assertNull(s3.getResource("s3-test.xlsx"));

    // NCP Object Storage
    NcpObjectStorageProvider ncp = new NcpObjectStorageProvider("ncp-bucket", "kr");
    assertEquals(StorageType.NCP, ncp.getType());
    ncp.storeFile(
        sample,
        "ncp-test.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertNotNull(ncp.getResource("ncp-test.xlsx"));
    ncp.delete("ncp-test.xlsx");
    assertNull(ncp.getResource("ncp-test.xlsx"));

    // Azure Blob Storage
    AzureBlobStorageProvider azure = new AzureBlobStorageProvider("azure-cont", "fake-conn");
    assertEquals(StorageType.AZURE, azure.getType());
    azure.storeFile(
        sample,
        "azure-test.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertNotNull(azure.getResource("azure-test.xlsx"));
    azure.delete("azure-test.xlsx");
    assertNull(azure.getResource("azure-test.xlsx"));

    // GCP Cloud Storage
    GcpCloudStorageProvider gcp = new GcpCloudStorageProvider("gcp-bucket", "proj-123");
    assertEquals(StorageType.GCP, gcp.getType());
    gcp.storeFile(
        sample,
        "gcp-test.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertNotNull(gcp.getResource("gcp-test.xlsx"));
    gcp.delete("gcp-test.xlsx");
    assertNull(gcp.getResource("gcp-test.xlsx"));
  }

  @Test
  @DisplayName("StorageService facade delegates correctly to active provider")
  void testStorageServiceFacade() throws Exception {
    Path sample = tempDir.resolve("facade.xlsx");
    Files.writeString(sample, "facade test");

    LocalDiskStorageProvider local = new LocalDiskStorageProvider(tempDir.resolve("facade-root"));
    StorageService service = new StorageService(local);

    String key =
        service.storeFile(
            sample,
            "facade.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertEquals("facade.xlsx", key);

    assertTrue(service.getResource(key).isPresent());
    service.deleteFile(key);
    assertTrue(service.getResource(key).isEmpty());
  }
}
