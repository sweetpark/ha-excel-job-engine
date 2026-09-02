package io.github.sweetpark.haexcel.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Registry for data providers. Supports auto-detection of Spring beans as well as programmatic
 * registration.
 */
public class ExcelDataRegistry implements ApplicationContextAware {

  private static final Logger log = LoggerFactory.getLogger(ExcelDataRegistry.class);

  private final Map<String, ExcelDataProvider> providers = new ConcurrentHashMap<>();
  private ApplicationContext applicationContext;

  public void register(ExcelDataProvider provider) {
    providers.put(provider.getName(), provider);
    log.info("[ExcelDataRegistry] Registered provider: {}", provider.getName());
  }

  public Object resolve(String bizNm) {
    ExcelDataProvider provider = providers.get(bizNm);
    if (provider != null) {
      return provider;
    }
    if (applicationContext != null) {
      // Check for direct bean or prefixed bean
      if (applicationContext.containsBean(bizNm)) {
        return applicationContext.getBean(bizNm);
      }
      String prefixed = "read|" + bizNm;
      if (applicationContext.containsBean(prefixed)) {
        return applicationContext.getBean(prefixed);
      }
    }
    return null;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
    // Auto-discover all ExcelDataProvider beans
    Map<String, ExcelDataProvider> beans =
        applicationContext.getBeansOfType(ExcelDataProvider.class);
    beans.values().forEach(this::register);
  }
}
