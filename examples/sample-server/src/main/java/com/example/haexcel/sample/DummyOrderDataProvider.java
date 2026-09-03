package com.example.haexcel.sample;

import io.github.sweetpark.haexcel.core.ExcelDataProvider;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Generates dummy order rows on demand so the sample server can be exported without wiring up a
 * real database table. Registering an {@link ExcelDataProvider} bean is all a consumer needs to
 * do to expose a new export endpoint - this class doubles as the minimal reference example.
 *
 * <p>The bizNm ({@code orderList}) and field names match the {@code examples/client-react} demo,
 * so the two examples work together out of the box.
 */
@Component
public class DummyOrderDataProvider implements ExcelDataProvider {

  private static final String[] STATUS_CODES = {"01", "02", "03"};
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  @Override
  public String getName() {
    return "orderList"; // reachable at POST /api/excel/orderList
  }

  // Fixed row count kept small so the demo export finishes instantly - the request's totalCnt
  // (e.g. 150,000 in the client-react demo) is only a client-side routing hint and is not passed
  // through to ExcelDataProvider#fetchData.
  private static final int ROW_COUNT = 500;

  @Override
  public List<Map<String, Object>> fetchData(Map<String, Object> params) {
    int count = ROW_COUNT;
    Random random = new Random(42);
    List<Map<String, Object>> rows = new ArrayList<>(count);
    for (int i = 1; i <= count; i++) {
      rows.add(
          Map.of(
              "orderNo", "ORD-" + String.format("%08d", i),
              "customerName", "customer_" + i,
              "amount", random.nextInt(1_000_000),
              "status", STATUS_CODES[random.nextInt(STATUS_CODES.length)],
              "orderedAt", LocalDateTime.now().minusMinutes(i).format(TIMESTAMP_FORMAT)));
    }
    return rows;
  }
}
