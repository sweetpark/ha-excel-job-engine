import { ExcelColumnDef } from '../hooks/useExcelExport';

/**
 * Adapter utility converting AG Grid Column Definitions into HA Excel Column Definitions.
 */
export function convertAgGridToExcelColumns(agColDefs: any[]): ExcelColumnDef[] {
  return agColDefs
    .filter((col) => !col.hide && col.field)
    .map((col) => {
      let excelFormat: ExcelColumnDef['excelFormat'] = undefined;
      const fieldLower = String(col.field).toLowerCase();

      // Auto-detect format based on column type or field name
      if (col.type === 'numericColumn' || fieldLower.includes('amount') || fieldLower.includes('price')) {
        excelFormat = 'krw';
      } else if (fieldLower.includes('datetime') || fieldLower.includes('time')) {
        excelFormat = 'datetime';
      } else if (fieldLower.includes('date') || fieldLower.includes('ymd')) {
        excelFormat = 'ymd';
      } else if (fieldLower.includes('bizno')) {
        excelFormat = 'bizno';
      } else if (fieldLower.includes('phone') || fieldLower.includes('tel')) {
        excelFormat = 'phone';
      }

      return {
        field: col.field,
        headerName: col.headerName || col.field,
        width: col.width || col.minWidth || 120,
        excelFormat,
        cellStyle: col.cellStyle,
        children: col.children ? convertAgGridToExcelColumns(col.children) : undefined,
      };
    });
}
