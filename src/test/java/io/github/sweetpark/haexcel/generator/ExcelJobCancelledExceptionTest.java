package io.github.sweetpark.haexcel.generator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExcelJobCancelledExceptionTest {

  @Test
  void testException() {
    ExcelJobCancelledException ex = new ExcelJobCancelledException("Job was cancelled");
    assertEquals("Job was cancelled", ex.getMessage());
  }
}
