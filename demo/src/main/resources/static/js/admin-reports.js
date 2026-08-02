(() => {
  const form = document.querySelector('#report-filter');
  const from = document.querySelector('#from');
  const to = document.querySelector('#to');
  const error = document.querySelector('#report-error');
  const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });
  const query = () => new URLSearchParams({ from: from.value, to: to.value });
  const endpoints = ['revenue', 'daily-revenue', 'payment-methods', 'top-dishes', 'inventory-consumption', 'shifts'];
  const csv = { 'revenue-csv': 'revenue', 'daily-csv': 'daily-revenue', 'methods-csv': 'payment-methods', 'dishes-csv': 'top-dishes', 'inventory-csv': 'inventory-consumption', 'shifts-csv': 'shifts' };
  const value = item => item == null ? '—' : item;
  const set = (id, text) => document.querySelector(id).textContent = text;
  const fetchReport = async endpoint => { const response = await fetch(`/api/admin/reports/${endpoint}?${query()}`); if (!response.ok) throw new Error(`Không thể tải báo cáo (${response.status})`); return response.json(); };
  const fill = (id, rows, columns) => { const body = document.querySelector(id); body.replaceChildren(); if (!rows.length) { const cell = document.createElement('td'); cell.colSpan = columns; cell.textContent = 'Không có dữ liệu trong khoảng đã chọn.'; const row = document.createElement('tr'); row.append(cell); body.append(row); return; } rows.forEach(row => { const tr = document.createElement('tr'); row.forEach(cellValue => { const td = document.createElement('td'); td.textContent = value(cellValue); tr.append(td); }); body.append(tr); }); };
  const setCsvUrls = () => Object.entries(csv).forEach(([id, endpoint]) => { document.querySelector(`#${id}`).href = `/api/admin/reports/${endpoint}/export.csv?${query()}`; });
  async function load() {
    if (!from.value || !to.value || from.value > to.value) { error.hidden = false; error.textContent = 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.'; return; }
    error.hidden = true; endpoints.forEach(endpoint => document.querySelectorAll('.report-kpi-card strong').forEach(node => { if (node.textContent === '—') node.textContent = '…'; }));
    try {
      const [revenue, daily, methods, dishes, inventory, shifts] = await Promise.all(endpoints.map(fetchReport));
      set('#gross-kpi', money.format(revenue.grossSales || 0)); set('#refund-kpi', money.format(revenue.refundTotal || 0)); set('#net-kpi', money.format(revenue.netRevenue || 0)); set('#transactions-kpi', revenue.paidOrderCount || 0); set('#average-kpi', money.format(revenue.averageOrderValue || 0)); set('#shifts-kpi', shifts.length);
      fill('#daily-body', daily.map(x => [x.date, money.format(x.grossSales || 0), money.format(x.refundTotal || 0), money.format(x.netRevenue || 0), x.paidOrderCount || 0]), 5);
      fill('#methods-body', methods.map(x => [x.paymentMethod, money.format(x.grossAmount || 0), money.format(x.refundAmount || 0), money.format(x.netAmount || 0)]), 4);
      fill('#dishes-body', dishes.map(x => [x.dishName, x.quantitySold || 0, money.format(x.revenue || 0)]), 3);
      fill('#inventory-body', inventory.map(x => [x.inventoryItemName, x.unit, x.netConsumedQuantity || 0]), 3);
      fill('#shifts-body', shifts.map(x => [x.cashierUsername, x.status, money.format(x.totalSales || 0), x.orderCount || 0]), 4);
      setCsvUrls();
    } catch (exception) { error.hidden = false; error.textContent = exception.message; }
  }
  const today = new Date().toISOString().slice(0, 10); from.value ||= today; to.value ||= today;
  form.addEventListener('submit', event => { event.preventDefault(); load(); }); form.addEventListener('reset', () => setTimeout(load)); load();
})();
