# 🌐 REST API Reference Guide

`ha-excel-job-engine` provides standard RESTful endpoints for distributed, asynchronous Excel file generation, real-time progress tracking, and file downloads.

---

## 📑 Endpoints Overview

| Method | Endpoint | Description | Request Body | Response Code |
|---|---|---|---|:---:|
| `GET` | `/api/excel/config` | Retrieve client configuration (e.g., client export threshold) | None | `200 OK` |
| `POST` | `/api/excel/{bizNm}` | Submit an asynchronous Excel export job | `ExcelRequest` | `202 Accepted` |
| `GET` | `/api/excel/{jobId}/status` | Check job status, progress percentage, and queue wait time | None | `200 OK` / `404 Not Found` |
| `GET` | `/api/excel/{jobId}/file` | Download completed `.xlsx` or `.zip` file | None | `200 OK` / `404 Not Found` |
| `DELETE` | `/api/excel/{jobId}/cancel` | Cancel a pending or running export job | None | `200 OK` / `403 Forbidden` |
| `GET` | `/api/excel/template/{templateId}` | Download original Excel template file | None | `200 OK` / `404 Not Found` |

---

## 1. GET `/api/excel/config`
Retrieves threshold configuration used by frontend clients to decide between client-side direct export and server-side background export.

### Request
```http
GET /api/excel/config HTTP/1.1
Host: localhost:8080
```

### Response (`200 OK`)
```json
{
  "clientThreshold": 10000
}
```
> **Client Strategy**:
> - If total row count `< clientThreshold` (e.g., < 10,000): Frontend exports directly using browser memory (SheetJS, AG Grid export).
> - If total row count `>= clientThreshold`: Frontend delegates to `POST /api/excel/{bizNm}` for asynchronous server processing.

---

## 2. POST `/api/excel/{bizNm}`
Submits an asynchronous export job. If an identical active job with matching parameters is already in `PENDING` state within the idempotency window (default 30 min), the existing `jobId` is returned.

### Request
```http
POST /api/excel/orderList HTTP/1.1
Host: localhost:8080
Content-Type: application/json
X-User-Id: user_12345

{
  "fileName": "2026_Q3_Orders",
  "totalCnt": 150000,
  "params": {
    "status": "PAID",
    "startDate": "20260901",
    "endDate": "20260930"
  },
  "columns": [
    {
      "field": "orderNo",
      "headerName": "Order Number",
      "width": 140
    },
    {
      "field": "customerName",
      "headerName": "Customer Name",
      "width": 100
    },
    {
      "field": "amount",
      "headerName": "Amount (KRW)",
      "width": 120,
      "excelFormat": "krw",
      "cellStyle": {
        "textAlign": "right",
        "fontWeight": "bold",
        "color": "#0000FF"
      }
    },
    {
      "field": "status",
      "headerName": "Status",
      "width": 100,
      "excelCodeMap": {
        "01": "Pending",
        "02": "Paid",
        "03": "Cancelled"
      }
    },
    {
      "field": "orderedAt",
      "headerName": "Ordered Time",
      "width": 160,
      "excelFormat": "datetime"
    }
  ]
}
```

### Column Definition Attributes (`ExcelColumnDef`)

| Field | Type | Description | Example |
|---|---|---|---|
| `field` | String | Data model key mapping to row map | `"orderNo"` |
| `headerName` | String | Column header display text | `"Order Number"` |
| `width` | Integer | Column width in pixels (converted to POI units) | `120` |
| `minWidth` | Integer | Fallback minimum width | `80` |
| `excelFormat` | String | Value formatter key (`krw`, `ymd`, `datetime`, `pct`, etc.) | `"krw"` |
| `cellStyle` | Map | Cell formatting (`textAlign`, `fontWeight`, `color`, `backgroundColor`) | `{"textAlign":"right"}` |
| `excelCodeMap` | Map | Code-to-Label dictionary mapping | `{"01":"Active"}` |
| `children` | Array | Nested child column definitions for multi-level grouped headers | `[...]` |

#### Supported `excelFormat` Values

| Format Key | Format Applied | Input Value | Output Display |
|---|---|---|---|
| `krw` | Comma-separated currency | `1250000` | `1,250,000` |
| `ymd` | Date (YYYY/MM/DD) | `"20260902"` | `2026/09/02` |
| `tm` | Time (HH:mm:ss) | `"143025"` | `14:30:25` |
| `datetime` | Date Time | `"20260902143025"` | `2026/09/02 14:30:25` |
| `bizno` | Business Registration Number | `"1234567890"` | `123-45-67890` |
| `phone` | Mobile Phone | `"01012345678"` | `010-1234-5678` |
| `tel` | Landline Phone | `"021234567"` | `02-123-4567` |
| `pct` | Percentage | `15` / `0` | `15%` / `"-"` |

#### Multi-Level Grouped Headers Example
```json
{
  "field": "customerGroup",
  "headerName": "Customer Information",
  "children": [
    { "field": "customerId", "headerName": "ID", "width": 80 },
    { "field": "customerName", "headerName": "Name", "width": 100 }
  ]
}
```

### Response (`202 Accepted`)
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "PENDING"
}
```

---

## 3. GET `/api/excel/{jobId}/status`
Polls the execution status, processed rows, and queue wait time of a submitted job.

### Request
```http
GET /api/excel/f47ac10b-58cc-4372-a567-0e02b2c3d479/status HTTP/1.1
Host: localhost:8080
X-User-Id: user_12345
```

### Response: `PENDING` (Waiting in Queue)
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "PENDING",
  "processedRows": 0,
  "totalRows": 150000,
  "queuePosition": 3,
  "estimatedSeconds": 90
}
```

### Response: `RUNNING` (Processing)
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "RUNNING",
  "processedRows": 75000,
  "totalRows": 150000
}
```
> **Progress Calculation**: `(processedRows / totalRows) * 100` = `50%`.

### Response: `DONE` (Completed, Ready to Download)
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "DONE",
  "processedRows": 150000,
  "totalRows": 150000
}
```

### Response: `FAIL` (Failed or Cancelled)
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "FAIL",
  "processedRows": 45000,
  "totalRows": 150000,
  "errorMsg": "Cancelled by user"
}
```

---

## 4. GET `/api/excel/{jobId}/file`
Downloads the completed export file. Returns an `.xlsx` workbook or a `.zip` archive (if total rows exceeded `zipThreshold`).

### Request
```http
GET /api/excel/f47ac10b-58cc-4372-a567-0e02b2c3d479/file HTTP/1.1
Host: localhost:8080
X-User-Id: user_12345
```

### Response Headers (`200 OK`)
```http
HTTP/1.1 200 OK
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename*=UTF-8''2026_Q3_Orders_20260902170000.xlsx
Content-Length: 1548230
```
> *(For ZIP chunked downloads)*
> `Content-Type: application/zip`
> `Content-Disposition: attachment; filename*=UTF-8''2026_Q3_Orders_20260902170000.zip`

---

## 5. DELETE `/api/excel/{jobId}/cancel`
Requests cancellation of a running or pending export job.

### Request
```http
DELETE /api/excel/f47ac10b-58cc-4372-a567-0e02b2c3d479/cancel HTTP/1.1
Host: localhost:8080
X-User-Id: user_12345
```

### Response (`200 OK`)
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "message": "Cancel request accepted."
}
```

### Response (`403 Forbidden`)
Returned if the user is not authorized to cancel the job (IDOR defense).
```json
{
  "error": "Unauthorized to cancel job: f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```
