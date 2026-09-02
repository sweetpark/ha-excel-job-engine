package io.github.sweetpark.haexcel.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Definition of a single Excel column.
 *
 * <p>Supports single-row flat headers and multi-level grouped headers with children.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExcelColumnDef(
    @JsonProperty("field") String field,
    @JsonProperty("headerName") String headerName,
    @JsonProperty("width") Integer width,
    @JsonProperty("minWidth") Integer minWidth,
    @JsonProperty("children") List<ExcelColumnDef> children,
    @JsonProperty("excelFormat") String excelFormat,
    @JsonProperty("cellStyle") Map<String, String> cellStyle,
    @JsonProperty("excelCodeMap") Map<String, String> excelCodeMap) {

  public ExcelColumnDef(String field, String headerName) {
    this(field, headerName, null, null, null, null, null, null);
  }

  public ExcelColumnDef(String field, String headerName, Integer width) {
    this(field, headerName, width, null, null, null, null, null);
  }

  public ExcelColumnDef(
      String field, String headerName, Integer width, List<ExcelColumnDef> children) {
    this(field, headerName, width, null, children, null, null, null);
  }

  public Integer effectiveWidth() {
    if (width != null && width > 0) return width;
    if (minWidth != null && minWidth > 0) return minWidth;
    return null;
  }

  public boolean isLeaf() {
    return children == null || children.isEmpty();
  }
}
