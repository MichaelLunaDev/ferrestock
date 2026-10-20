import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { saveDownload, validateReport } from './api';
import { useAuth } from './auth';
import { type Report, type ReportKind, type ReportQuery, type Sale, type Stock, type TopProduct } from './types';
import { Busy, decimal, Empty, ErrorNotice, Heading, Pagination, useAction } from './ui';

const tabs: Array<{ kind: ReportKind; title: string; description: string }> = [
 { kind: 'ventas', title: 'Ventas por período', description: 'Consulta las ventas del período seleccionado.' },
 { kind: 'productos-mas-vendidos', title: 'Productos más vendidos', description: 'Revisa unidades vendidas e importes históricos por producto.' },
 { kind: 'stock-bajo', title: 'Stock bajo', description: 'Identifica productos con stock menor o igual al umbral indicado.' },
];
const today = () => { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; };
export function Reports() {
 const { client } = useAuth(); const action = useAction(); const [query, setQuery] = useState<ReportQuery>({ kind: 'ventas', desde: today().slice(0, 8) + '01', hasta: today(), top: '10', umbral: '5' }); const [result, setResult] = useState<Report | null>(null); const [submitted, setSubmitted] = useState<ReportQuery | null>(null); const [page, setPage] = useState(0); const [validation, setValidation] = useState('');
 const tab = tabs.find(t => t.kind === query.kind)!;
 function change(patch: Partial<ReportQuery>) { setQuery(q => ({ ...q, ...patch })); setResult(null); setSubmitted(null); setPage(0); action.setError(null); setValidation(''); }
 function tabKey(event: KeyboardEvent, kind: ReportKind) {
  const current = tabs.findIndex(t => t.kind === kind);
  const next = event.key === 'ArrowRight' ? (current + 1) % tabs.length : event.key === 'ArrowLeft' ? (current + tabs.length - 1) % tabs.length : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : -1;
  if (next >= 0 && !action.busy) { event.preventDefault(); change({ kind: tabs[next].kind }); document.getElementById(`tab-${tabs[next].kind}`)?.focus(); }
 }
 function execute(format: 'JSON' | 'PDF' | 'XLSX') {
  const error = validateReport(query); if (error) { setValidation(error); return; }
  const snapshot = { ...query }; setValidation('');
  void action.run(async () => {
   if (format === 'JSON') { setResult(null); setSubmitted(null); const response = await client.report(snapshot); setResult(response); setSubmitted(snapshot); setPage(0); }
   else { const blob = await client.download(snapshot, format); saveDownload(blob, snapshot.kind, format); }
  });
 }
 function submit(event: FormEvent) { event.preventDefault(); execute('JSON'); }
 const rows = result?.filas.slice(page * 25, (page + 1) * 25) ?? [];
 return <><Heading eyebrow="INFORMACIÓN DEL NEGOCIO" title="Reportes">Consulta, compara y exporta la información que necesitas.</Heading><div className="tabs" role="tablist" aria-label="Tipo de reporte">{tabs.map(t => <button key={t.kind} role="tab" id={`tab-${t.kind}`} aria-controls="report-panel" aria-selected={t.kind === query.kind} tabIndex={t.kind === query.kind ? 0 : -1} onKeyDown={e => tabKey(e, t.kind)} disabled={action.busy} onClick={() => change({ kind: t.kind })}>{t.title}</button>)}</div><section id="report-panel" role="tabpanel" aria-labelledby={`tab-${query.kind}`} className="panel"><div className="panel-toolbar"><div><h2>{tab.title}</h2><p>{tab.description}</p></div><div className="export-actions"><button className="button secondary" disabled={action.busy} onClick={() => execute('PDF')}>↓ PDF</button><button className="button secondary" disabled={action.busy} onClick={() => execute('XLSX')}>↓ Excel</button></div></div><form className="report-filters" onSubmit={submit}><fieldset disabled={action.busy}><legend className="sr-only">Filtros del reporte</legend>{query.kind !== 'stock-bajo' ? <><label>Desde<input type="date" required min="0001-01-01" max="9999-12-30" value={query.desde} onChange={e => change({ desde: e.target.value })}/></label><label>Hasta<input type="date" required min="0001-01-01" max="9999-12-30" value={query.hasta} onChange={e => change({ hasta: e.target.value })}/></label>{query.kind === 'productos-mas-vendidos' && <label>Cantidad de productos<input type="number" min={1} max={100} step={1} required value={query.top} onChange={e => change({ top: e.target.value })}/></label>}</> : <label>Umbral de stock<input type="number" min={0} max={1000000} step={1} required value={query.umbral} onChange={e => change({ umbral: e.target.value })}/></label>}<button className="button" disabled={action.busy}>{action.busy ? 'Consultando…' : 'Consultar reporte'}</button></fieldset></form>{validation && <p className="notice danger" role="alert">{validation}</p>}<ErrorNotice error={action.error}/>{action.busy ? <Busy/> : result && submitted ? <><div className="result-caption"><strong>{result.filas.length.toLocaleString('es-PE')} filas recibidas</strong><span>{submitted.kind === 'stock-bajo' ? `Stock ≤ ${submitted.umbral}` : `${submitted.desde} → ${submitted.hasta}`}</span></div>{rows.length ? <div className="table-scroll"><table><thead><tr>{(submitted.kind === 'ventas' ? ['Venta', 'Fecha comercial', 'Vendedor', 'Total', 'Estado'] : submitted.kind === 'productos-mas-vendidos' ? ['Producto', 'SKU', 'Nombre', 'Unidades', 'Importe'] : ['Producto', 'SKU', 'Nombre', 'Stock']).map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={index}>{cells(submitted.kind, row).map((cell, i) => <td key={i}>{cell}</td>)}</tr>)}</tbody></table></div> : <Empty title="Sin registros para estos filtros">Prueba otro período o umbral de stock.</Empty>}<Pagination page={page} next={(page + 1) * 25 < result.filas.length} busy={action.busy} change={setPage}/></> : !action.error && <Empty title="Tu próxima consulta empieza aquí">Elige tus filtros para consultar el reporte. Sólo se mostrarán datos devueltos por el servicio.</Empty>}<p className="panel-footnote">PDF y Excel utilizan los mismos filtros. Los reportes por período admiten hasta 366 días inclusivos y un máximo de 5 000 filas.</p></section></>;
}
function cells(kind: ReportKind, row: Sale | TopProduct | Stock): string[] {
 if (kind === 'ventas') { const x = row as Sale; return [x.id, x.fecha.replace('T', ' '), x.vendedor, decimal(x.total), x.estado]; }
 if (kind === 'productos-mas-vendidos') { const x = row as TopProduct; return [x.productoId, x.sku, x.nombre, decimal(x.unidades), decimal(x.importe)]; }
 const x = row as Stock; return [x.productoId, x.sku, x.nombre, decimal(x.stock)];
}
