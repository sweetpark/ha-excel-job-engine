package io.github.sweetpark.haexcel.autoconfigure;

import io.github.sweetpark.haexcel.storage.StorageType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for High-Availability Excel Engine. */
@ConfigurationProperties(prefix = "ha-excel")
public class ExcelProperties {

  /** Client (browser) export threshold. Below this count, browser can export directly. */
  private int clientThreshold = 10_000;

  /** Completed job file TTL in minutes. Expired files are deleted by eviction scheduler. */
  private int jobTtlMinutes = 60;

  /** Temporary directory for file processing. */
  private String tempDir = System.getProperty("java.io.tmpdir") + "/ha-excel-jobs";

  /** Worker thread pool size for normal (single xlsx) queue. */
  private int workerCount = 4;

  /** Worker thread pool size for large (chunked zip) queue. */
  private int largeWorkerCount = 2;

  /** Server node identifier. If blank, hostname is used. */
  private String serverId = "";

  /** Idempotency check window in minutes to detect duplicate submissions. */
  private int idempotencyWindowMinutes = 30;

  /** Threshold in hours to consider a PENDING job orphaned and reclaim it. */
  private int orphanThresholdHours = 2;

  /** Streaming row count threshold (0 = always stream if streamable). */
  private int streamingThreshold = 0;

  /** Rough estimate in seconds per job for queue waiting time calculations. */
  private int estimatedSecondsPerJob = 30;

  /** Threshold row count to split into chunked ZIP files (0 = always single xlsx). */
  private int zipThreshold = 100_000;

  /** Maximum rows per chunk file in ZIP mode. */
  private int chunkSize = 50_000;

  /** Storage provider type: LOCAL, NAS, S3, NCP, AZURE, GCP. */
  private StorageType storageType = StorageType.LOCAL;

  private String localStoragePath = System.getProperty("java.io.tmpdir") + "/ha-excel-storage";
  private String nasStoragePath = System.getProperty("java.io.tmpdir") + "/ha-excel-nas";
  private String s3Bucket = "excel-export-bucket";
  private String s3Region = "ap-northeast-2";
  private String s3Endpoint = "";
  private String ncpBucket = "ncp-excel-bucket";
  private String ncpRegion = "kr";
  private String ncpEndpoint = "https://kr.object.ncloudstorage.com";
  private String azureContainer = "excel-container";
  private String azureConnectionString = "";
  private String gcpBucket = "gcp-excel-bucket";
  private String gcpProjectId = "ha-excel-project";

  public int getClientThreshold() {
    return clientThreshold;
  }

  public void setClientThreshold(int clientThreshold) {
    this.clientThreshold = clientThreshold;
  }

  public int getJobTtlMinutes() {
    return jobTtlMinutes;
  }

  public void setJobTtlMinutes(int jobTtlMinutes) {
    this.jobTtlMinutes = jobTtlMinutes;
  }

  public String getTempDir() {
    return tempDir;
  }

  public void setTempDir(String tempDir) {
    this.tempDir = tempDir;
  }

  public int getWorkerCount() {
    return workerCount;
  }

  public void setWorkerCount(int workerCount) {
    this.workerCount = workerCount;
  }

  public int getLargeWorkerCount() {
    return largeWorkerCount;
  }

  public void setLargeWorkerCount(int largeWorkerCount) {
    this.largeWorkerCount = largeWorkerCount;
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(String serverId) {
    this.serverId = serverId;
  }

  public int getIdempotencyWindowMinutes() {
    return idempotencyWindowMinutes;
  }

  public void setIdempotencyWindowMinutes(int idempotencyWindowMinutes) {
    this.idempotencyWindowMinutes = idempotencyWindowMinutes;
  }

  public int getOrphanThresholdHours() {
    return orphanThresholdHours;
  }

  public void setOrphanThresholdHours(int orphanThresholdHours) {
    this.orphanThresholdHours = orphanThresholdHours;
  }

  public int getStreamingThreshold() {
    return streamingThreshold;
  }

  public void setStreamingThreshold(int streamingThreshold) {
    this.streamingThreshold = streamingThreshold;
  }

  public int getEstimatedSecondsPerJob() {
    return estimatedSecondsPerJob;
  }

  public void setEstimatedSecondsPerJob(int estimatedSecondsPerJob) {
    this.estimatedSecondsPerJob = estimatedSecondsPerJob;
  }

  public int getZipThreshold() {
    return zipThreshold;
  }

  public void setZipThreshold(int zipThreshold) {
    this.zipThreshold = zipThreshold;
  }

  public int getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  public StorageType getStorageType() {
    return storageType;
  }

  public void setStorageType(StorageType storageType) {
    this.storageType = storageType;
  }

  public String getLocalStoragePath() {
    return localStoragePath;
  }

  public void setLocalStoragePath(String localStoragePath) {
    this.localStoragePath = localStoragePath;
  }

  public String getNasStoragePath() {
    return nasStoragePath;
  }

  public void setNasStoragePath(String nasStoragePath) {
    this.nasStoragePath = nasStoragePath;
  }

  public String getS3Bucket() {
    return s3Bucket;
  }

  public void setS3Bucket(String s3Bucket) {
    this.s3Bucket = s3Bucket;
  }

  public String getS3Region() {
    return regionOr(s3Region);
  }

  private String regionOr(String r) {
    return r != null ? r : "ap-northeast-2";
  }

  public void setS3Region(String s3Region) {
    this.s3Region = s3Region;
  }

  public String getS3Endpoint() {
    return s3Endpoint;
  }

  public void setS3Endpoint(String s3Endpoint) {
    this.s3Endpoint = s3Endpoint;
  }

  public String getNcpBucket() {
    return ncpBucket;
  }

  public void setNcpBucket(String ncpBucket) {
    this.ncpBucket = ncpBucket;
  }

  public String getNcpRegion() {
    return ncpRegion;
  }

  public void setNcpRegion(String ncpRegion) {
    this.ncpRegion = ncpRegion;
  }

  public String getNcpEndpoint() {
    return ncpEndpoint;
  }

  public void setNcpEndpoint(String ncpEndpoint) {
    this.ncpEndpoint = ncpEndpoint;
  }

  public String getAzureContainer() {
    return azureContainer;
  }

  public void setAzureContainer(String azureContainer) {
    this.azureContainer = azureContainer;
  }

  public String getAzureConnectionString() {
    return azureConnectionString;
  }

  public void setAzureConnectionString(String azureConnectionString) {
    this.azureConnectionString = azureConnectionString;
  }

  public String getGcpBucket() {
    return gcpBucket;
  }

  public void setGcpBucket(String gcpBucket) {
    this.gcpBucket = gcpBucket;
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }

  public void setGcpProjectId(String gcpProjectId) {
    this.gcpProjectId = gcpProjectId;
  }
}
