package io.github.sweetpark.haexcel.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class ExcelDataRegistryTest {

  @Test
  void testRegisterAndResolve() {
    ExcelDataRegistry registry = new ExcelDataRegistry();

    ExcelDataProvider provider =
        new ExcelDataProvider() {
          @Override
          public String getName() {
            return "orderData";
          }

          @Override
          public List<Map<String, Object>> fetchData(Map<String, Object> params) {
            return List.of(Map.of("id", 1));
          }
        };

    registry.register(provider);
    assertEquals(provider, registry.resolve("orderData"));
    assertNull(registry.resolve("nonExistent"));

    // Test with mock ApplicationContext
    ApplicationContext ctx = mock(ApplicationContext.class);
    when(ctx.getBeansOfType(ExcelDataProvider.class)).thenReturn(Map.of("beanProvider", provider));
    when(ctx.containsBean("customBean")).thenReturn(true);
    Object dummyBean = new Object();
    when(ctx.getBean("customBean")).thenReturn(dummyBean);
    when(ctx.containsBean("read|prefixedBean")).thenReturn(true);
    when(ctx.getBean("read|prefixedBean")).thenReturn(dummyBean);

    registry.setApplicationContext(ctx);
    assertEquals(dummyBean, registry.resolve("customBean"));
    assertEquals(dummyBean, registry.resolve("prefixedBean"));
  }
}
