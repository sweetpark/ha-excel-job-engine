package io.github.sweetpark.haexcel.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sweetpark.haexcel.controller.DefaultExcelSecurityProvider;
import io.github.sweetpark.haexcel.controller.ExcelController;
import io.github.sweetpark.haexcel.controller.ExcelSecurityProvider;
import io.github.sweetpark.haexcel.core.ExcelDataRegistry;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.ExcelJobMapper;
import io.github.sweetpark.haexcel.core.ExcelJobQueue;
import io.github.sweetpark.haexcel.core.ExcelWorkerService;
import io.github.sweetpark.haexcel.generator.ExcelGeneratorService;
import io.github.sweetpark.haexcel.generator.ExcelZipGeneratorService;
import io.github.sweetpark.haexcel.generator.TemplateExcelGeneratorService;
import io.github.sweetpark.haexcel.storage.StorageProvider;
import io.github.sweetpark.haexcel.storage.StorageService;
import io.github.sweetpark.haexcel.storage.azure.AzureBlobStorageProvider;
import io.github.sweetpark.haexcel.storage.gcp.GcpCloudStorageProvider;
import io.github.sweetpark.haexcel.storage.local.LocalDiskStorageProvider;
import io.github.sweetpark.haexcel.storage.nas.NasStorageProvider;
import io.github.sweetpark.haexcel.storage.ncp.NcpObjectStorageProvider;
import io.github.sweetpark.haexcel.storage.s3.AwsS3StorageProvider;
import io.github.sweetpark.haexcel.template.ExcelTemplateService;
import io.github.sweetpark.haexcel.template.JxlsTemplateEngine;
import io.github.sweetpark.haexcel.template.TemplateExcelEngine;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot Starter AutoConfiguration for high-availability distributed Excel job engine. */
@AutoConfiguration
@ConditionalOnClass({SXSSFWorkbook.class, ExcelJobMapper.class})
@EnableConfigurationProperties(ExcelProperties.class)
@EnableScheduling
public class ExcelAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(SqlSessionFactory.class)
  @ConditionalOnBean(SqlSessionFactory.class)
  @MapperScan(basePackages = "io.github.sweetpark.haexcel.core", annotationClass = Mapper.class)
  static class ExcelMapperConfiguration {}

  @Bean
  @ConditionalOnMissingBean(ObjectMapper.class)
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  @ConditionalOnMissingBean(StorageProvider.class)
  public StorageProvider storageProvider(ExcelProperties props) {
    return switch (props.getStorageType()) {
      case NAS -> new NasStorageProvider(Path.of(props.getNasStoragePath()));
      case S3 -> new AwsS3StorageProvider(
          props.getS3Bucket(), props.getS3Region(), props.getS3Endpoint());
      case NCP -> new NcpObjectStorageProvider(
          props.getNcpBucket(), props.getNcpRegion(), props.getNcpEndpoint());
      case AZURE -> new AzureBlobStorageProvider(
          props.getAzureContainer(), props.getAzureConnectionString());
      case GCP -> new GcpCloudStorageProvider(props.getGcpBucket(), props.getGcpProjectId());
      default -> new LocalDiskStorageProvider(Path.of(props.getLocalStoragePath()));
    };
  }

  @Bean
  @ConditionalOnMissingBean(StorageService.class)
  public StorageService storageService(StorageProvider storageProvider) {
    return new StorageService(storageProvider);
  }

  @Bean("normalJobQueue")
  @ConditionalOnMissingBean(name = "normalJobQueue")
  public ExcelJobQueue normalJobQueue() {
    return new ExcelJobQueue("normal");
  }

  @Bean("largeJobQueue")
  @ConditionalOnMissingBean(name = "largeJobQueue")
  public ExcelJobQueue largeJobQueue() {
    return new ExcelJobQueue("large");
  }

  @Bean
  @ConditionalOnMissingBean(ExcelDataRegistry.class)
  public ExcelDataRegistry excelDataRegistry() {
    return new ExcelDataRegistry();
  }

  @Bean
  @ConditionalOnMissingBean(TemplateExcelEngine.class)
  public TemplateExcelEngine templateExcelEngine() {
    return new JxlsTemplateEngine();
  }

  @Bean
  @ConditionalOnMissingBean(ExcelTemplateService.class)
  public ExcelTemplateService excelTemplateService() {
    return new ExcelTemplateService();
  }

  @Bean
  @ConditionalOnMissingBean(ExcelJobManager.class)
  public ExcelJobManager excelJobManager(
      ExcelProperties props,
      ExcelJobMapper jobMapper,
      StorageService storageService,
      @Autowired(required = false) ObjectMapper objectMapper,
      @Qualifier("normalJobQueue") ExcelJobQueue normalJobQueue,
      @Qualifier("largeJobQueue") ExcelJobQueue largeJobQueue) {
    ObjectMapper mapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    return new ExcelJobManager(
        props, jobMapper, storageService, mapper, normalJobQueue, largeJobQueue);
  }

  @Bean
  @ConditionalOnMissingBean(ExcelGeneratorService.class)
  public ExcelGeneratorService excelGeneratorService(
      ExcelProperties props,
      ExcelJobManager jobManager,
      StorageService storageService,
      ExcelDataRegistry dataRegistry) {
    return new ExcelGeneratorService(props, jobManager, storageService, dataRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(ExcelZipGeneratorService.class)
  public ExcelZipGeneratorService excelZipGeneratorService(
      ExcelProperties props,
      ExcelJobManager jobManager,
      StorageService storageService,
      ExcelDataRegistry dataRegistry) {
    return new ExcelZipGeneratorService(props, jobManager, storageService, dataRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(TemplateExcelGeneratorService.class)
  public TemplateExcelGeneratorService templateExcelGeneratorService(
      ExcelProperties props,
      ExcelJobManager jobManager,
      StorageService storageService,
      ExcelTemplateService templateService,
      TemplateExcelEngine engine,
      ExcelDataRegistry dataRegistry) {
    return new TemplateExcelGeneratorService(
        props, jobManager, storageService, templateService, engine, dataRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(ExcelWorkerService.class)
  public ExcelWorkerService excelWorkerService(
      ExcelProperties props,
      ExcelJobMapper jobMapper,
      ExcelJobManager jobManager,
      ExcelGeneratorService generatorService,
      ExcelZipGeneratorService zipGeneratorService,
      TemplateExcelGeneratorService templateGeneratorService,
      @Qualifier("normalJobQueue") ExcelJobQueue normalJobQueue,
      @Qualifier("largeJobQueue") ExcelJobQueue largeJobQueue) {
    return new ExcelWorkerService(
        props,
        jobMapper,
        jobManager,
        generatorService,
        zipGeneratorService,
        templateGeneratorService,
        normalJobQueue,
        largeJobQueue);
  }

  @Bean
  @ConditionalOnMissingBean(ExcelSecurityProvider.class)
  public ExcelSecurityProvider excelSecurityProvider() {
    return new DefaultExcelSecurityProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ExcelController.class)
  @ConditionalOnProperty(
      prefix = "ha-excel.controller",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ExcelController excelController(
      ExcelJobManager jobManager,
      ExcelTemplateService templateService,
      ExcelProperties props,
      ExcelSecurityProvider securityProvider) {
    return new ExcelController(jobManager, templateService, props, securityProvider);
  }
}
