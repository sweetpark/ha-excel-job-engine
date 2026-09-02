import React from 'react';
import { ExcelExportButton } from './components/ExcelExportButton';
import { ExcelColumnDef } from './hooks/useExcelExport';

export const App: React.FC = () => {
  // Sample column definitions with formatters and styling
  const columns: ExcelColumnDef[] = [
    { field: 'orderNo', headerName: 'Order Number', width: 140 },
    { field: 'customerName', headerName: 'Customer Name', width: 120 },
    {
      field: 'amount',
      headerName: 'Amount (KRW)',
      width: 120,
      excelFormat: 'krw',
      cellStyle: { textAlign: 'right', fontWeight: 'bold', color: '#0050b3' },
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 100,
      excelCodeMap: { '01': 'Pending', '02': 'Paid', '03': 'Refunded' },
    },
    { field: 'orderedAt', headerName: 'Order Timestamp', width: 160, excelFormat: 'datetime' },
  ];

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', background: '#fff', padding: '32px', borderRadius: '12px', boxShadow: '0 2px 8px rgba(0,0,0,0.08)' }}>
      <h2>📊 HA Excel Job Engine - Client Demo</h2>
      <p style={{ color: '#666', lineHeight: 1.6 }}>
        This client demonstrates seamless integration with <code>ha-excel-job-engine</code>.<br />
        It initiates asynchronous background export, displays real-time progress percentages and queue wait estimates, allows safe cancellation, and automatically triggers browser download upon completion.
      </p>

      <div style={{ marginTop: '24px', padding: '20px', background: '#fafafa', borderRadius: '8px' }}>
        <h4>Simulated Export Configuration:</h4>
        <ul>
          <li><b>Business Query (bizNm):</b> <code>orderList</code></li>
          <li><b>Total Row Count:</b> 150,000 rows (Exceeds clientThreshold ➔ Async Processing)</li>
          <li><b>Chunked ZIP Threshold:</b> 100,000 rows (Will download as .zip archive)</li>
        </ul>

        <div style={{ marginTop: '20px' }}>
          <ExcelExportButton
            bizNm="orderList"
            fileName="Q3_Order_Report"
            totalCnt={150000}
            params={{ status: 'PAID', year: 2026 }}
            columns={columns}
          />
        </div>
      </div>
    </div>
  );
};
