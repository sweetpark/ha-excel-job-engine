package io.github.sweetpark.haexcel.core;

import java.util.List;
import java.util.Map;

/**
 * Generic data provider interface for Excel export. Implement this interface to provide in-memory
 * or custom data sources for specific bizNm keys.
 */
public interface ExcelDataProvider {

  /** Identifier for this data provider (matches bizNm in request). */
  String getName();

  /** Fetches entire dataset as a list of maps. */
  List<Map<String, Object>> fetchData(Map<String, Object> params);

  /** Returns true if this provider supports streaming. */
  default boolean isStreamable() {
    return this instanceof ExcelStreamable;
  }
}
