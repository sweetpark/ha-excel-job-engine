package io.github.sweetpark.haexcel.template;

import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/** Loads Excel templates from classpath:excel/{templateId}.xlsx. */
public class ExcelTemplateService {

  private static final Logger log = LoggerFactory.getLogger(ExcelTemplateService.class);
  private static final String TEMPLATE_BASE_PATH = "excel/";

  public byte[] getTemplate(String templateId) throws IOException {
    String resourcePath = TEMPLATE_BASE_PATH + templateId + ".xlsx";
    ClassPathResource resource = new ClassPathResource(resourcePath);

    if (!resource.exists()) {
      log.warn("[ExcelTemplateService] Template not found at: classpath:{}", resourcePath);
      throw new IllegalArgumentException("Excel template not found: " + resourcePath);
    }

    try (InputStream in = resource.getInputStream()) {
      return in.readAllBytes();
    }
  }
}
