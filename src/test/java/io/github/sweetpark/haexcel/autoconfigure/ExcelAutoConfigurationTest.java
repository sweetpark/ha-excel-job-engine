package io.github.sweetpark.haexcel.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.github.sweetpark.haexcel.core.ExcelJobMapper;
import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageType;
import io.github.sweetpark.haexcel.storage.ncp.NcpObjectStorageProvider;
import io.github.sweetpark.haexcel.storage.s3.AwsS3StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ExcelAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class))
          .withBean(ExcelJobMapper.class, () -> mock(ExcelJobMapper.class));

  @Test
  @DisplayName("ExcelAutoConfiguration loads storage and registry beans by default")
  void testAutoConfigurationBeans() {
    contextRunner.run(
        context -> {
          assertTrue(context.containsBean("storageProvider"));
          assertTrue(context.containsBean("storageService"));
          assertTrue(context.containsBean("normalJobQueue"));
          assertTrue(context.containsBean("largeJobQueue"));
          assertTrue(context.containsBean("excelDataRegistry"));
          assertTrue(context.containsBean("excelSecurityProvider"));
          assertTrue(context.containsBean("excelJobManager"));
          assertTrue(context.containsBean("excelWorkerService"));
          assertTrue(context.containsBean("excelController"));

          StorageProvider provider = context.getBean(StorageProvider.class);
          assertNotNull(provider);
        });
  }

  @Test
  @DisplayName("ExcelAutoConfiguration wires an AwsS3StorageProvider for storage-type=S3")
  void testS3StorageProviderSelected() {
    contextRunner
        .withPropertyValues(
            "ha-excel.storage-type=S3",
            "ha-excel.s3-bucket=test-bucket",
            "ha-excel.s3-region=us-east-1",
            "ha-excel.s3-access-key=test-key",
            "ha-excel.s3-secret-key=test-secret")
        .run(
            context -> {
              StorageProvider provider = context.getBean(StorageProvider.class);
              assertInstanceOf(AwsS3StorageProvider.class, provider);
              assertEquals(StorageType.S3, provider.getType());
            });
  }

  @Test
  @DisplayName("ExcelAutoConfiguration wires an NcpObjectStorageProvider for storage-type=NCP")
  void testNcpStorageProviderSelected() {
    contextRunner
        .withPropertyValues(
            "ha-excel.storage-type=NCP",
            "ha-excel.ncp-bucket=test-bucket",
            "ha-excel.ncp-region=kr")
        .run(
            context -> {
              StorageProvider provider = context.getBean(StorageProvider.class);
              assertInstanceOf(NcpObjectStorageProvider.class, provider);
              assertEquals(StorageType.NCP, provider.getType());
            });
  }

  @Test
  @DisplayName("ExcelAutoConfiguration wires a GcpCloudStorageProvider for storage-type=GCP")
  void testGcpStorageProviderSelected() {
    contextRunner
        .withPropertyValues(
            "ha-excel.storage-type=GCP",
            "ha-excel.gcp-bucket=test-bucket",
            "ha-excel.gcp-project-id=test-project")
        .run(
            context -> {
              StorageProvider provider = context.getBean(StorageProvider.class);
              assertEquals(StorageType.GCP, provider.getType());
            });
  }

  @Test
  @DisplayName("requireSdkOnClasspath passes through when the class is present")
  void testRequireSdkOnClasspathPresent() {
    assertDoesNotThrow(
        () ->
            ExcelAutoConfiguration.requireSdkOnClasspath(
                "java.lang.String", "some:coordinate", StorageType.S3));
  }

  @Test
  @DisplayName(
      "requireSdkOnClasspath fails fast with an actionable message when the SDK is missing")
  void testRequireSdkOnClasspathMissing() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                ExcelAutoConfiguration.requireSdkOnClasspath(
                    "com.example.DefinitelyNotOnTheClasspath",
                    "com.example:fake-sdk",
                    StorageType.AZURE));
    assertTrue(ex.getMessage().contains("com.example:fake-sdk"));
    assertTrue(ex.getMessage().contains("AZURE"));
  }
}
