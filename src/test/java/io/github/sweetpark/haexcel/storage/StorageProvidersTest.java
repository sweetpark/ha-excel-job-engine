package io.github.sweetpark.haexcel.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sweetpark.haexcel.storage.local.LocalDiskStorageProvider;
import io.github.sweetpark.haexcel.storage.nas.NasStorageProvider;
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

  // Cloud storage providers (S3, NCP, Azure, GCP) are covered by CloudStorageProvidersTest,
  // which exercises the real SDK calls against Testcontainers-backed emulators (MinIO / Azurite /
  // fake-gcs-server) instead of asserting against an in-memory mock.

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
