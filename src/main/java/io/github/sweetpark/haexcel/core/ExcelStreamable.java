package io.github.sweetpark.haexcel.core;

import java.util.Map;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;

/** Interface for streaming row data via MyBatis Cursor to prevent Out-Of-Memory (OOM). */
public interface ExcelStreamable {

  /**
   * Streams rows using the provided SqlSession. The caller will manage the session lifecycle.
   *
   * @param params Query parameters
   * @param sqlSession Active SqlSession provided by caller
   * @return Row-by-row Cursor
   */
  Cursor<Map<String, Object>> streamRows(Map<String, Object> params, SqlSession sqlSession);
}
