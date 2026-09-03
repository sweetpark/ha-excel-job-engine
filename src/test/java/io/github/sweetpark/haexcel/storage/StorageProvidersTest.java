package io.github.sweetpark.haexcel.storage;

import static org.junit.jupiter.api.Assertions.*;

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

  // storeFile/getResource/delete for S3, NCP, Azure, and GCP are covered by
  // CloudStorageProvidersTest, which exercises the real SDK calls against Testcontainers-backed
  // emulators (MinIO / Azurite / fake-gcs-server) instead of asserting against an in-memory mock.
  // The construction/getter tests below don't need a live endpoint - client construction in the
  // AWS and GCP SDKs is lazy (no network call until the first real operation), so these run
  // without Docker.

  @Test
  @DisplayName("AwsS3StorageProvider exposes its configuration and closes cleanly")
  void testS3ProviderConstructionVariants() throws Exception {
    try (AwsS3StorageProvider withEndpointAndCreds =
        new AwsS3StorageProvider(
            "bucket-a", "us-east-1", "http://localhost:9000", "key", "secret")) {
      assertEquals(StorageType.S3, withEndpointAndCreds.getType());
      assertEquals("bucket-a", withEndpointAndCreds.getBucketName());
      assertEquals("us-east-1", withEndpointAndCreds.getRegion());
      assertEquals("http://localhost:9000", withEndpointAndCreds.getEndpoint());
    }

    // No endpoint override, no static credentials -> default AWS region/credential chain branch
    // (the S3Client itself falls back to ap-northeast-2 internally; getRegion() reflects the raw
    // constructor argument, which stays null here).
    try (AwsS3StorageProvider defaults = new AwsS3StorageProvider("bucket-b", null)) {
      assertNull(defaults.getRegion());
      assertNull(defaults.getEndpoint());
    }
  }

  @Test
  @DisplayName("NcpObjectStorageProvider falls back to the default NCP endpoint when blank")
  void testNcpProviderConstructionVariants() throws Exception {
    try (NcpObjectStorageProvider withBlankEndpoint =
        new NcpObjectStorageProvider("ncp-bucket", "kr", "", "key", "secret")) {
      assertEquals(StorageType.NCP, withBlankEndpoint.getType());
    }
    try (NcpObjectStorageProvider defaults = new NcpObjectStorageProvider("ncp-bucket", "kr")) {
      assertEquals(StorageType.NCP, defaults.getType());
    }
  }

  @Test
  @DisplayName("GcpCloudStorageProvider exposes its configuration")
  void testGcpProviderConstructionVariants() {
    GcpCloudStorageProvider withHost =
        new GcpCloudStorageProvider("gcp-bucket", "test-project", "http://localhost:4443");
    assertEquals(StorageType.GCP, withHost.getType());
    assertEquals("gcp-bucket", withHost.getBucketName());
    assertEquals("test-project", withHost.getProjectId());

    GcpCloudStorageProvider defaults = new GcpCloudStorageProvider("gcp-bucket", "test-project");
    assertEquals("test-project", defaults.getProjectId());
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
