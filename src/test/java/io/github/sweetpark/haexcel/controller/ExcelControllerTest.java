package io.github.sweetpark.haexcel.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.core.domain.ExcelJobStatus;
import io.github.sweetpark.haexcel.core.domain.ExcelRequest;
import io.github.sweetpark.haexcel.template.ExcelTemplateService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExcelControllerTest {

  private MockMvc mockMvc;
  private ExcelJobManager jobManager;
  private ExcelTemplateService templateService;
  private ExcelProperties props;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    jobManager = mock(ExcelJobManager.class);
    templateService = mock(ExcelTemplateService.class);
    props = new ExcelProperties();
    objectMapper = new ObjectMapper();

    ExcelController controller =
        new ExcelController(jobManager, templateService, props, new DefaultExcelSecurityProvider());
    // Pin the JSON converter explicitly: standaloneSetup() otherwise auto-detects converters
    // purely from what's on the classpath, and also registers an XML converter as soon as
    // jackson-dataformat-xml is present (pulled in transitively by the Azure Blob SDK test
    // dependency) - without an explicit Accept header that could win content negotiation and
    // serialize responses as XML instead of JSON.
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
  }

  @Test
  @DisplayName("GET /api/excel/config returns clientThreshold")
  void testConfigEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/excel/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientThreshold").value(10000));
  }

  @Test
  @DisplayName("POST /api/excel/{bizNm} submits job and returns 202 Accepted")
  void testSubmitJob() throws Exception {
    ExcelJob job = ExcelJob.builder().jobId("test-job-uuid").status(ExcelJobStatus.PENDING).build();

    when(jobManager.createJob(
            anyString(), anyString(), anyString(), anyMap(), anyList(), any(), anyInt()))
        .thenReturn(job);

    ExcelRequest request =
        new ExcelRequest(
            Map.of("category", "electronics"),
            List.of(new ExcelColumnDef("id", "ID")),
            "test-export",
            100,
            null);

    mockMvc
        .perform(
            post("/api/excel/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("test-job-uuid"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @DisplayName("GET /api/excel/{jobId}/status returns job status and progress")
  void testStatusEndpoint() throws Exception {
    ExcelJob job =
        ExcelJob.builder()
            .jobId("status-job")
            .worker("anonymous")
            .status(ExcelJobStatus.RUNNING)
            .processedRows(50)
            .totalRows(100)
            .build();

    when(jobManager.findJob("status-job")).thenReturn(Optional.of(job));

    mockMvc
        .perform(get("/api/excel/status-job/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value("status-job"))
        .andExpect(jsonPath("$.status").value("RUNNING"))
        .andExpect(jsonPath("$.processedRows").value(50))
        .andExpect(jsonPath("$.totalRows").value(100));
  }

  @Test
  @DisplayName("DELETE /api/excel/{jobId}/cancel requests job cancellation")
  void testCancelEndpoint() throws Exception {
    doNothing().when(jobManager).requestCancel(eq("cancel-job"), anyString());

    mockMvc
        .perform(delete("/api/excel/cancel-job/cancel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value("cancel-job"));
  }
}
