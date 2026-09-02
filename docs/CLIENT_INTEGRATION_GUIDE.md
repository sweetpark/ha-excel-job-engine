# 💻 Frontend Client Integration Guide

This guide explains how frontend applications (React, Vue, or Vanilla JS) interact with **HA Excel Job Engine** to provide users with smooth, non-blocking Excel downloads with real-time progress bars.

---

## 🎯 Architecture & Decision Flow

```
[ User clicks "Download Excel" ]
               │
               ▼
   Fetch total row count (N)
               │
       Is N < clientThreshold?
      ┌────────┴────────┐
     YES               NO
      ▼                 ▼
Direct Browser     POST /api/excel/{bizNm}
Download           (Submit Async Job)
(e.g., SheetJS)         │
                        ▼
                   Poll GET /api/excel/{jobId}/status
                   (Display Progress % & Estimated Seconds)
                        │
                        ▼
                   Status == DONE?
                        │
                        ▼
                   GET /api/excel/{jobId}/file
                   (Trigger Browser Download)
```

---

## ⚛️ React Custom Hook: `useExcelExport`

A production-ready React hook that handles submission, polling, progress tracking, cancellation, and automated browser file download.

```typescript
import { useState, useRef, useCallback } from 'react';

export interface ColumnDef {
  field: string;
  headerName: string;
  width?: number;
  excelFormat?: string;
  cellStyle?: Record<string, string>;
  excelCodeMap?: Record<string, string>;
}

export interface ExportOptions {
  bizNm: string;
  fileName: string;
  totalCnt: number;
  params: Record<string, any>;
  columns: ColumnDef[];
}

export function useExcelExport() {
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState<'IDLE' | 'PENDING' | 'RUNNING' | 'DONE' | 'FAIL'>('IDLE');
  const [queueInfo, setQueueInfo] = useState<{ queuePosition?: number; estimatedSeconds?: number }>({});
  const [error, setError] = useState<string | null>(null);

  const activeJobIdRef = useRef<string | null>(null);
  const pollTimerRef = useRef<NodeJS.Timeout | null>(null);

  const clearTimer = () => {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  const cancelExport = useCallback(async () => {
    if (!activeJobIdRef.current) return;
    try {
      await fetch(`/api/excel/${activeJobIdRef.current}/cancel`, { method: 'DELETE' });
    } finally {
      clearTimer();
      setLoading(false);
      setStatus('FAIL');
      setError('Cancelled by user');
      activeJobIdRef.current = null;
    }
  }, []);

  const triggerDownload = (jobId: string) => {
    const link = document.createElement('a');
    link.href = `/api/excel/${jobId}/file`;
    link.download = '';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const startExport = useCallback(async (options: ExportOptions) => {
    setLoading(true);
    setError(null);
    setProgress(0);
    setStatus('PENDING');

    try {
      // 1. Submit Job
      const res = await fetch(`/api/excel/${options.bizNm}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(options),
      });

      if (!res.ok) throw new Error('Failed to submit export job');

      const data = await res.json();
      const jobId = data.jobId;
      activeJobIdRef.current = jobId;

      // 2. Poll Status Loop
      const poll = async () => {
        try {
          const statusRes = await fetch(`/api/excel/${jobId}/status`);
          if (!statusRes.ok) throw new Error('Failed to fetch job status');

          const statusData = await statusRes.json();
          setStatus(statusData.status);

          if (statusData.status === 'PENDING') {
            setQueueInfo({
              queuePosition: statusData.queuePosition,
              estimatedSeconds: statusData.estimatedSeconds,
            });
            pollTimerRef.current = setTimeout(poll, 2000);
          } else if (statusData.status === 'RUNNING') {
            const pct = statusData.totalRows > 0
              ? Math.min(100, Math.round((statusData.processedRows / statusData.totalRows) * 100))
              : 0;
            setProgress(pct);
            pollTimerRef.current = setTimeout(poll, 1500);
          } else if (statusData.status === 'DONE') {
            setProgress(100);
            setLoading(false);
            triggerDownload(jobId);
            activeJobIdRef.current = null;
          } else if (statusData.status === 'FAIL') {
            throw new Error(statusData.errorMsg || 'Export job failed');
          }
        } catch (err: any) {
          setError(err.message);
          setLoading(false);
          setStatus('FAIL');
          activeJobIdRef.current = null;
        }
      };

      poll();
    } catch (err: any) {
      setError(err.message);
      setLoading(false);
      setStatus('FAIL');
    }
  }, []);

  return {
    startExport,
    cancelExport,
    loading,
    progress,
    status,
    queueInfo,
    error,
  };
}
```

---

## 🎨 UI Example Component (React)

```tsx
import React from 'react';
import { useExcelExport } from './useExcelExport';

export const ExportButton = ({ orders, totalCount, searchParams }) => {
  const { startExport, cancelExport, loading, progress, status, queueInfo, error } = useExcelExport();

  const handleExport = () => {
    startExport({
      bizNm: 'orderList',
      fileName: 'Order_Report',
      totalCnt: totalCount,
      params: searchParams,
      columns: [
        { field: 'orderId', headerName: 'Order #', width: 120 },
        { field: 'customerName', headerName: 'Customer', width: 100 },
        { field: 'amount', headerName: 'Total', width: 110, excelFormat: 'krw' },
        { field: 'orderedAt', headerName: 'Date', width: 140, excelFormat: 'datetime' },
      ],
    });
  };

  return (
    <div>
      <button onClick={handleExport} disabled={loading}>
        {loading ? 'Exporting...' : 'Export to Excel'}
      </button>

      {loading && (
        <div style={{ marginTop: '10px' }}>
          {status === 'PENDING' && (
            <p>Waiting in queue (Position: {queueInfo.queuePosition}, ~{queueInfo.estimatedSeconds}s left)</p>
          )}

          {status === 'RUNNING' && (
            <div>
              <progress value={progress} max="100" />
              <span>{progress}%</span>
            </div>
          )}

          <button onClick={cancelExport} style={{ marginLeft: '8px' }}>Cancel</button>
        </div>
      )}

      {error && <p style={{ color: 'red' }}>{error}</p>}
    </div>
  );
};
```

---

## 🔄 AG Grid Column Definition Adapter

If you are using **AG Grid**, you can automatically convert AG Grid's `ColDef[]` into `ExcelColumnDef[]`:

```typescript
export function adaptAgGridColumns(agColDefs: any[]): ColumnDef[] {
  return agColDefs
    .filter((col) => !col.hide && col.field)
    .map((col) => {
      let excelFormat: string | undefined;

      if (col.type === 'numericColumn' || col.valueFormatter?.toString().includes('currency')) {
        excelFormat = 'krw';
      } else if (col.field.toLowerCase().includes('date')) {
        excelFormat = 'datetime';
      }

      return {
        field: col.field,
        headerName: col.headerName || col.field,
        width: col.width || col.minWidth || 100,
        excelFormat,
        children: col.children ? adaptAgGridColumns(col.children) : undefined,
      };
    });
}
```
