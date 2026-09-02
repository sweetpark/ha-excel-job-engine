package io.github.sweetpark.haexcel.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Strategy interface for template-based Excel generation. */
public interface TemplateExcelEngine {

  void fill(InputStream templateInput, Map<String, Object> context, OutputStream output)
      throws IOException;
}
