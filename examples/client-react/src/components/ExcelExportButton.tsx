import React from 'react';
import { useExcelExport, ExcelColumnDef } from '../hooks/useExcelExport';

interface Props {
  bizNm: string;
  fileName: string;
  totalCnt: number;
  params: Record<string, any>;
  columns: ExcelColumnDef[];
}

export const ExcelExportButton: React.FC<Props> = ({
  bizNm,
  fileName,
  totalCnt,
  params,
  columns,
}) => {
  const {
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
  } = useExcelExport();

  const handleExportClick = () => {
    startExport({
      bizNm,
      fileName,
      totalCnt,
      params,
      columns,
    });
  };

  return (
    <div style={{ display: 'inline-block', position: 'relative' }}>
      <button
        onClick={handleExportClick}
        disabled={loading}
        style={{
          backgroundColor: loading ? '#8c8c8c' : '#1890ff',
          color: '#ffffff',
          border: 'none',
          padding: '10px 18px',
          fontSize: '14px',
          fontWeight: 600,
          borderRadius: '6px',
          cursor: loading ? 'not-allowed' : 'pointer',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        }}
      >
        <span>📊</span>
        <span>{loading ? 'Processing Export...' : 'Download Excel'}</span>
      </button>

      {/* Real-time Progress & Modal View */}
      {loading && (
        <div
          style={{
            position: 'fixed',
            bottom: '24px',
            right: '24px',
            width: '360px',
            padding: '20px',
            backgroundColor: '#ffffff',
            borderRadius: '12px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
            border: '1px solid #e8e8e8',
            zIndex: 9999,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h4 style={{ margin: 0, fontSize: '16px' }}>Exporting {fileName}</h4>
            <span style={{ fontSize: '12px', color: '#595959' }}>{status}</span>
          </div>

          {status === 'PENDING' && (
            <div style={{ marginTop: '12px', color: '#595959', fontSize: '13px' }}>
              <p style={{ margin: '4px 0' }}>
                ⏳ Queue Position: <b>{queuePosition ?? 1}</b>
              </p>
              <p style={{ margin: '4px 0' }}>
                ⏱ Estimated Wait: ~<b>{estimatedSeconds ?? 30}s</b>
              </p>
            </div>
          )}

          {status === 'RUNNING' && (
            <div style={{ marginTop: '14px' }}>
              <div
                style={{
                  width: '100%',
                  height: '8px',
                  backgroundColor: '#f0f0f0',
                  borderRadius: '4px',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${progress}%`,
                    height: '100%',
                    backgroundColor: '#52c41a',
                    transition: 'width 0.3s ease',
                  }}
                />
              </div>

              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  marginTop: '6px',
                  fontSize: '12px',
                  color: '#8c8c8c',
                }}
              >
                <span>{processedRows.toLocaleString()} / {totalRows.toLocaleString()} rows</span>
                <span>{progress}%</span>
              </div>
            </div>
          )}

          <div style={{ marginTop: '16px', display: 'flex', justifyContent: 'flex-end' }}>
            <button
              onClick={() => cancelExport()}
              style={{
                backgroundColor: '#fff1f0',
                color: '#ff4d4f',
                border: '1px solid #ffccc7',
                padding: '6px 14px',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '12px',
              }}
            >
              Cancel Export
            </button>
          </div>
        </div>
      )}

      {error && (
        <div style={{ marginTop: '8px', color: '#ff4d4f', fontSize: '12px' }}>
          ⚠️ {error}
        </div>
      )}
    </div>
  );
};
