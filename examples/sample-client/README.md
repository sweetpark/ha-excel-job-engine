# 💻 HA Excel Client React Example

A runnable React + TypeScript client demonstrating integration with **HA Excel Job Engine**.

---

## 🌟 What This Example Includes

- **`useExcelExport` Hook**: Complete asynchronous export lifecycle hook.
  - Submits job to `POST /api/excel/{bizNm}`
  - Polls `GET /api/excel/{jobId}/status` with automatic wait interval
  - Computes progress percentage: `(processedRows / totalRows) * 100`
  - Displays queue waiting time and queue position
  - Supports cooperative cancellation via `DELETE /api/excel/{jobId}/cancel`
  - Automatically triggers browser file download when `status === 'DONE'`
- **`ExcelExportButton` Component**: Ready-to-use button with floating progress modal and cancel button.
- **AG Grid Adapter (`convertAgGridToExcelColumns`)**: Converts AG Grid's `ColDef[]` into `ExcelColumnDef[]`.

---

## 🚀 Running the Example

### 1. Install Dependencies
```bash
npm install
```

### 2. Start Development Server
```bash
npm run dev
```
Open `http://localhost:3000` in your browser.  
Requests to `/api/*` are automatically proxied to the backend at `http://localhost:8080`.
