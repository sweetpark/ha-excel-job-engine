CREATE TABLE IF NOT EXISTS ha_excel_job (
    job_id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    biz_nm         VARCHAR(128) NOT NULL,
    file_name      VARCHAR(256) NOT NULL,
    worker         VARCHAR(64)  NOT NULL,
    server_id      VARCHAR(128),
    status         VARCHAR(20)  NOT NULL,
    processed_rows INT          NOT NULL DEFAULT 0,
    total_rows     INT          NOT NULL DEFAULT 0,
    file_path      VARCHAR(512),
    params_json    TEXT,
    columns_json   TEXT,
    template_id    VARCHAR(128),
    error_msg      VARCHAR(1000),
    cancel_yn      CHAR(1)      DEFAULT 'N',
    created_at     BIGINT       NOT NULL,
    started_at     BIGINT,
    completed_at   BIGINT
);

CREATE INDEX IF NOT EXISTS idx_ha_excel_status_created ON ha_excel_job (status, created_at);
CREATE INDEX IF NOT EXISTS idx_ha_excel_active_check   ON ha_excel_job (worker, biz_nm, status, created_at);
CREATE INDEX IF NOT EXISTS idx_ha_excel_server_status  ON ha_excel_job (server_id, status);