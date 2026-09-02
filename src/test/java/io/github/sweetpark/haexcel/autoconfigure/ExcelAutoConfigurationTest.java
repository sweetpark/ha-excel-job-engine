package io.github.sweetpark.haexcel.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.github.sweetpark.haexcel.core.ExcelJobMapper;
import io.github.sweetpark.haexcel.storage.StorageProvider;
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
}
