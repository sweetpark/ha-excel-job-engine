package io.github.sweetpark.haexcel.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExcelDataProviderTest {

  @Test
  void testDefaultMethods() {
    ExcelDataProvider provider =
        new ExcelDataProvider() {
          @Override
          public String getName() {
            return "testProvider";
          }

          @Override
          public List<Map<String, Object>> fetchData(Map<String, Object> params) {
            return List.of();
          }
        };

    assertEquals("testProvider", provider.getName());
    assertTrue(provider.fetchData(Map.of()).isEmpty());
    assertFalse(provider.isStreamable());
  }
}
