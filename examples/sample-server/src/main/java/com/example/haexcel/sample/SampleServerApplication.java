package com.example.haexcel.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal, runnable Spring Boot server that wires up ha-excel-job-engine end to end: H2
 * in-memory DB, a dummy {@code ExcelDataProvider}, and the library's own REST controller. Clone
 * this folder and run {@code ./gradlew bootRun} - no other setup required.
 */
@SpringBootApplication
public class SampleServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(SampleServerApplication.class, args);
  }
}
