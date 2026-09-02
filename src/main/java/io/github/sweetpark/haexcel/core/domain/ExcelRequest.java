package io.github.sweetpark.haexcel.core.domain;

import java.util.List;
import java.util.Map;

/** Excel export request DTO. */
public record ExcelRequest(
    Map<String, Object> params,
    List<ExcelColumnDef> columns,
    String fileName,
    int totalCnt,
    String templateId) {}
