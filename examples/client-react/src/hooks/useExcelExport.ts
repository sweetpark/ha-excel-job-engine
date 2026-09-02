import { useState, useRef, useCallback } from 'react';

export interface ExcelColumnDef {
  field: string;
  headerName: string;
  width?: number;
  minWidth?: number;
  excelFormat?: 'krw' | 'ymd' | 'tm' | 'datetime' | 'bizno' | 'phone' | 'tel' | 'pct';
  cellStyle?: {
    textAlign?: 'left' | 'center' | 'right';
    fontWeight?: 'bold' | 'normal';
    fontStyle?: 'italic' | 'normal';
    color?: string;
    backgroundColor?: string;
  };
  excelCodeMap?: Record<string, string>;
  children?: ExcelColumnDef[];
}

export interface ExcelExportOptions {
  bizNm: string;
  fileName: string;
  totalCnt: number;
  params?: Record<string, any>;
  columns: ExcelColumnDef[];
  templateId?: string;
  apiBaseUrl?: string;
  userId?: string;
  onSuccess?: () => void;
  onError?: (err: Error) => void;
}

export type ExportStatus = 'IDLE' | 'PENDING' | 'RUNNING' | 'DONE' | 'FAIL';

export function useExcelExport() {
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<ExportStatus>('IDLE');
  const [progress, setProgress] = useState(0);
  const [processedRows, setProcessedRows] = useState(0);
  const [totalRows, setTotalRows] = useState(0);
  const [queuePosition, setQueuePosition] = useState<number | null>(null);
  const [estimatedSeconds, setEstimatedSeconds] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const activeJobIdRef = useRef<string | null>(null);
  const pollTimerRef = useRef<NodeJS.Timeout | null>(null);

  const clearTimer = () => {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  const triggerBrowserDownload = (downloadUrl: string) => {
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = '';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const cancelExport = useCallback(async (apiBaseUrl: string = '/api/excel') => {
    const jobId = activeJobIdRef.current;
    if (!jobId) return;

    try {
      await fetch(`${apiBaseUrl}/${jobId}/cancel`, { method: 'DELETE' });
    } finally {
      clearTimer();
      setLoading(false);
      setStatus('FAIL');
      setError('Export cancelled by user.');
      activeJobIdRef.current = null;
    }
  }, []);

  const startExport = useCallback(async (options: ExcelExportOptions) => {
    const {
      bizNm,
      fileName,
      totalCnt,
      params = {},
      columns,
      templateId,
      apiBaseUrl = '/api/excel',
      userId,
      onSuccess,
      onError,
    } = options;

    setLoading(true);
    setStatus('PENDING');
    setProgress(0);
    setProcessedRows(0);
    setTotalRows(totalCnt);
    setError(null);
    setQueuePosition(null);
    setEstimatedSeconds(null);

    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (userId) {
        headers['X-User-Id'] = userId;
      }

      // 1. Submit asynchronous job
      const submitRes = await fetch(`${apiBaseUrl}/${bizNm}`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          fileName,
          totalCnt,
          params,
          columns,
          templateId,
        }),
      });

      if (!submitRes.ok) {
        throw new Error(`Failed to submit export job: ${submitRes.status} ${submitRes.statusText}`);
      }

      const submitData = await submitRes.json();
      const jobId = submitData.jobId;
      activeJobIdRef.current = jobId;

      // 2. Poll status recursively
      const poll = async () => {
        try {
          const statusRes = await fetch(`${apiBaseUrl}/${jobId}/status`, {
            headers: userId ? { 'X-User-Id': userId } : {},
          });

          if (!statusRes.ok) {
            throw new Error(`Failed to fetch job status: ${statusRes.status}`);
          }

          const jobInfo = await statusRes.json();
          const currentStatus: ExportStatus = jobInfo.status;
          setStatus(currentStatus);

          if (currentStatus === 'PENDING') {
            setQueuePosition(jobInfo.queuePosition ?? null);
            setEstimatedSeconds(jobInfo.estimatedSeconds ?? null);
            pollTimerRef.current = setTimeout(poll, 2000);
          } else if (currentStatus === 'RUNNING') {
            const processed = jobInfo.processedRows || 0;
            const total = jobInfo.totalRows || totalCnt || 1;
            setProcessedRows(processed);
            setTotalRows(total);

            const pct = Math.min(100, Math.round((processed / total) * 100));
            setProgress(pct);

            pollTimerRef.current = setTimeout(poll, 1500);
          } else if (currentStatus === 'DONE') {
            setProgress(100);
            setProcessedRows(jobInfo.totalRows);
            setLoading(false);
            activeJobIdRef.current = null;

            // Trigger file download
            triggerBrowserDownload(`${apiBaseUrl}/${jobId}/file`);
            if (onSuccess) onSuccess();
          } else if (currentStatus === 'FAIL') {
            const errMsg = jobInfo.errorMsg || 'Excel export job failed on server.';
            throw new Error(errMsg);
          }
        } catch (err: any) {
          setError(err.message || 'Error occurred during export polling.');
          setStatus('FAIL');
          setLoading(false);
          activeJobIdRef.current = null;
          if (onError) onError(err);
        }
      };

      poll();
    } catch (err: any) {
      setError(err.message || 'Failed to initialize export.');
      setStatus('FAIL');
      setLoading(false);
      if (onError) onError(err);
    }
  }, []);

  return {
    startExport,
    cancelExport,
    loading,
    status,
    progress,
    processedRows,
    totalRows,
    queuePosition,
    estimatedSeconds,
    error,
  };
}
