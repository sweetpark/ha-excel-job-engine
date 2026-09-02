package io.github.sweetpark.haexcel.core.domain;

/**
 * Excel generation job status enum.
 *
 * <pre>
 * PENDING -> RUNNING -> DONE
 *                    \-> FAIL
 * </pre>
 */
public enum ExcelJobStatus {
  PENDING,
  RUNNING,
  DONE,
  FAIL
}
