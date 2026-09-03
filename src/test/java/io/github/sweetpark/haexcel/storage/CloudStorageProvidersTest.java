package io.github.sweetpark.haexcel.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.github.sweetpark.haexcel.storage.azure.AzureBlobStorageProvider;
import io.github.sweetpark.haexcel.storage.gcp.GcpCloudStorageProvider;
import io.github.sweetpark.haexcel.storage.ncp.NcpObjectStorageProvider;
import io.github.sweetpark.haexcel.storage.s3.AwsS3StorageProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Verifies the cloud storage providers make real protocol calls (upload/download/delete) against
 * disposable emulator containers, rather than asserting against an in-memory mock. Requires a local
 * Docker daemon - the same requirement GitHub Actions' ubuntu-latest runners satisfy out of the
 * box.
 */
@Testcontainers
class CloudStorageProvidersTest {

  private static final String MINIO_USER = "minioadmin";
  private static final String MINIO_PASSWORD = "minioadmin";
  private static final String AZURITE_ACCOUNT_NAME = "devstoreaccount1";
  private static final String AZURITE_ACCOUNT_KEY =
      "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
  // Fixed (not dynamically mapped) so it can be baked into fake-gcs-server's -public-host flag
  // before the container starts. Arbitrary high port, unlikely to collide within a single CI job.
  private static final int FAKE_GCS_HOST_PORT = 44313;

  private static GenericContainer<?> minio;
  private static GenericContainer<?> azurite;
  private static GenericContainer<?> fakeGcs;

  @TempDir static Path tempDir;

  @BeforeAll
  static void startContainers() {
    minio =
        new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2024-01-01T16-36-33Z"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    minio.start();
    createS3Bucket(minioEndpoint(), "test-bucket");
    createS3Bucket(minioEndpoint(), "ncp-bucket");

    azurite =
        new GenericContainer<>(
                DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.30.0"))
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0")
            .withExposedPorts(10000)
            .waitingFor(Wait.forListeningPort());
    azurite.start();

    // fake-gcs-server's resumable upload protocol replies to the "start session" call with an
    // absolute Location URL that the client then PUTs the bytes to - it must be reachable from
    // this JVM, so unlike MinIO/Azurite (single-request PUT, any mapped port works) this needs a
    // host port known before the container starts, advertised back via -public-host.
    fakeGcs =
        new GenericContainer<>(DockerImageName.parse("fsouza/fake-gcs-server:1.49.2"))
            .withCommand("-scheme", "http", "-public-host", "localhost:" + FAKE_GCS_HOST_PORT)
            .withExposedPorts(4443)
            .withCreateContainerCmdModifier(
                cmd ->
                    cmd.getHostConfig()
                        .withPortBindings(
                            new PortBinding(
                                Ports.Binding.bindPort(FAKE_GCS_HOST_PORT), new ExposedPort(4443))))
            .waitingFor(Wait.forListeningPort());
    fakeGcs.start();
    Storage gcsAdmin =
        StorageOptions.newBuilder()
            .setHost(gcsEndpoint())
            .setProjectId("test-project")
            .setCredentials(com.google.cloud.NoCredentials.getInstance())
            .build()
            .getService();
    gcsAdmin.create(BucketInfo.of("gcp-bucket"));
  }

  @AfterAll
  static void stopContainers() {
    if (minio != null) {
      minio.stop();
    }
    if (azurite != null) {
      azurite.stop();
    }
    if (fakeGcs != null) {
      fakeGcs.stop();
    }
  }

  private static String minioEndpoint() {
    return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
  }

  private static String gcsEndpoint() {
    return "http://localhost:" + FAKE_GCS_HOST_PORT;
  }

  private static void createS3Bucket(String endpoint, String bucket) {
    try (S3Client admin =
        S3Client.builder()
            .region(Region.AP_NORTHEAST_2)
            .endpointOverride(java.net.URI.create(endpoint))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(MINIO_USER, MINIO_PASSWORD)))
            .build()) {
      admin.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }
  }

  @Test
  @DisplayName(
      "AwsS3StorageProvider stores, streams back, and deletes against a real S3 API (MinIO)")
  void testS3Storage() throws Exception {
    Path sample = tempDir.resolve("s3-sample.xlsx");
    Files.writeString(sample, "s3 test data");

    AwsS3StorageProvider s3 =
        new AwsS3StorageProvider(
            "test-bucket", "ap-northeast-2", minioEndpoint(), MINIO_USER, MINIO_PASSWORD);
    try {
      assertEquals(StorageType.S3, s3.getType());
      s3.storeFile(
          sample,
          "s3-test.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

      StorageResource resource = s3.getResource("s3-test.xlsx");
      assertNotNull(resource);
      assertEquals(Files.size(sample), resource.contentLength());
      assertEquals("s3 test data", new String(resource.resource().getInputStream().readAllBytes()));

      s3.delete("s3-test.xlsx");
      assertNull(s3.getResource("s3-test.xlsx"));
    } finally {
      s3.close();
    }
  }

  @Test
  @DisplayName("NcpObjectStorageProvider works against an S3-compatible endpoint (MinIO)")
  void testNcpStorage() throws Exception {
    Path sample = tempDir.resolve("ncp-sample.xlsx");
    Files.writeString(sample, "ncp test data");

    NcpObjectStorageProvider ncp =
        new NcpObjectStorageProvider(
            "ncp-bucket", "kr", minioEndpoint(), MINIO_USER, MINIO_PASSWORD);
    try {
      assertEquals(StorageType.NCP, ncp.getType());
      ncp.storeFile(
          sample,
          "ncp-test.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      assertNotNull(ncp.getResource("ncp-test.xlsx"));
      ncp.delete("ncp-test.xlsx");
      assertNull(ncp.getResource("ncp-test.xlsx"));
    } finally {
      ncp.close();
    }
  }

  @Test
  @DisplayName("AzureBlobStorageProvider stores, streams back, and deletes against Azurite")
  void testAzureStorage() throws Exception {
    Path sample = tempDir.resolve("azure-sample.xlsx");
    Files.writeString(sample, "azure test data");

    String connectionString =
        "DefaultEndpointsProtocol=http;AccountName="
            + AZURITE_ACCOUNT_NAME
            + ";AccountKey="
            + AZURITE_ACCOUNT_KEY
            + ";BlobEndpoint=http://"
            + azurite.getHost()
            + ":"
            + azurite.getMappedPort(10000)
            + "/"
            + AZURITE_ACCOUNT_NAME
            + ";";

    AzureBlobStorageProvider azure =
        new AzureBlobStorageProvider("excel-container", connectionString);
    assertEquals(StorageType.AZURE, azure.getType());
    azure.storeFile(
        sample,
        "azure-test.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    StorageResource resource = azure.getResource("azure-test.xlsx");
    assertNotNull(resource);
    assertEquals(
        "azure test data", new String(resource.resource().getInputStream().readAllBytes()));

    azure.delete("azure-test.xlsx");
    assertNull(azure.getResource("azure-test.xlsx"));
  }

  @Test
  @DisplayName("GcpCloudStorageProvider stores, streams back, and deletes against fake-gcs-server")
  void testGcpStorage() throws Exception {
    Path sample = tempDir.resolve("gcp-sample.xlsx");
    Files.writeString(sample, "gcp test data");

    GcpCloudStorageProvider gcp =
        new GcpCloudStorageProvider("gcp-bucket", "test-project", gcsEndpoint());
    assertEquals(StorageType.GCP, gcp.getType());
    gcp.storeFile(
        sample,
        "gcp-test.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    StorageResource resource = gcp.getResource("gcp-test.xlsx");
    assertNotNull(resource);
    assertEquals("gcp test data", new String(resource.resource().getInputStream().readAllBytes()));

    gcp.delete("gcp-test.xlsx");
    assertNull(gcp.getResource("gcp-test.xlsx"));
  }
}
