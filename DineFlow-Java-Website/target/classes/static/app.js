const money = n => '₹' + Number(n || 0).toFixed(2);
let menu = [], cart = {}, orderType = 'Dine-In', lastOrder = null, staffLogged = false, adminLogged = false, staffFilter = 'ALL';
const $ = id => document.getElementById(id);

async function api(path, opts = {}) {
  const token = localStorage.getItem('dineflow_token');
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }
  const r = await fetch('/api' + path, {
    credentials: 'same-origin',
    headers,
    ...opts
  });

  let d = {};
  try {
    d = await r.json();
  } catch {}

  if (!r.ok) {
    if (r.status === 401 && token && path !== '/staff/login' && path !== '/admin/login') {
      localStorage.removeItem('dineflow_token');
      staffLogged = false;
      adminLogged = false;
      const currentView = getHashView();
      if (currentView === 'staff') {
        $('staffBoard').classList.add('hidden');
        $('staffLogin').classList.remove('hidden');
      } else if (currentView === 'admin') {
        $('adminBoard').classList.add('hidden');
        $('adminLogin').classList.remove('hidden');
      }
    }
    throw new Error(d.message || d.error || 'Request failed');
  }
  return d;
}

function toast(s) {
  $('toast').textContent = s;
  $('toast').classList.remove('hidden');
  setTimeout(() => $('toast').classList.add('hidden'), 2600);
}

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

async function loadMenu() {
  try {
    menu = await api('/menu');
    const cats = ['All categories', ...new Set(menu.map(x => x.category))];
    $('category').innerHTML = cats.map(c => `<option>${esc(c)}</option>`).join('');
    renderMenu();
  } catch (e) {
    toast('Could not load menu: ' + e.message);
  }
}

function renderMenu() {
  const q = $('search').value.toLowerCase(), cat = $('category').value;
  const list = menu.filter(x => x.published && x.name.toLowerCase().includes(q) && (cat === 'All categories' || x.category === cat));
  $('menuGrid').innerHTML = list.length ? list.map(x => `<article class="dish-card"><div class="dish-art">${x.imageData ? `<img src="${x.imageData}" alt="${esc(x.name)}">` : esc(x.emoji || '🍽️')}</div><div class="dish-body"><div class="category-tag">${esc(x.category)}</div><div class="dish-title">${esc(x.name)}</div><div class="dish-bottom"><span>${money(x.price)}</span><button class="primary" onclick="addToCart(${x.id})">Add</button></div></div></article>`).join('') : '<p>No dishes found in this category.</p>';
}

function addToCart(id) {
  cart[id] = (cart[id] || 0) + 1;
  renderCart();
}

function changeQty(id, delta) {
  cart[id] = (cart[id] || 0) + delta;
  if (cart[id] <= 0) delete cart[id];
  renderCart();
}

function setType(btn) {
  document.querySelectorAll('#orderTypes button').forEach(b => b.classList.remove('selected'));
  btn.classList.add('selected');
  orderType = btn.dataset.type;
  $('tableWrap').classList.toggle('hidden', orderType !== 'Dine-In');
}

function renderCart() {
  const entries = Object.entries(cart).map(([id, q]) => ({ item: menu.find(x => x.id === Number(id)), q })).filter(x => x.item);
  $('cartContent').innerHTML = entries.length ? entries.map(({ item, q }) => `<div class="cart-row"><div class="cart-line"><span>${esc(item.name)}</span><span>${money(item.price * q)}</span></div><div class="cart-sub">${money(item.price)} each</div><div class="quantity"><button onclick="changeQty(${item.id},-1)">−</button><b>${q}</b><button onclick="changeQty(${item.id},1)">+</button></div></div>`).join('') : '<div class="empty">Your order is empty. Add dishes from the menu.</div>';
  const sub = entries.reduce((s, x) => s + x.item.price * x.q, 0);
  const coupon = $('coupon').value.trim().toUpperCase();
  const valid = coupon === 'SAVE10' && sub > 0;
  const disc = valid ? sub * .1 : 0;
  const tax = (sub - disc) * .05;
  const total = sub - disc + tax;
  $('subtotal').textContent = money(sub);
  $('discount').textContent = '− ' + money(disc);
  $('tax').textContent = money(tax);
  $('total').textContent = money(total);
  $('couponMessage').textContent = coupon ? (valid ? 'Coupon SAVE10 applied — 10% off' : 'Coupon not recognized') : '';
}

async function placeOrder() {
  const name = $('customerName').value.trim();
  const table = $('tableNumber').value.trim();
  const items = Object.entries(cart).map(([id, quantity]) => ({ id: Number(id), quantity }));
  if (!name) { toast('Please enter your name'); return; }
  if (orderType === 'Dine-In' && !table) { toast('Please enter a table number'); return; }
  if (!items.length) { toast('Add at least one dish first'); return; }
  try {
    const order = await api('/orders', {
      method: 'POST',
      body: JSON.stringify({ customerName: name, orderType, tableNumber: orderType === 'Dine-In' ? table : '—', coupon: $('coupon').value, items })
    });
    lastOrder = order;
    cart = {};
    $('coupon').value = '';
    renderCart();
    showReceipt(order);
    renderLatest(order);
    toast('Order placed successfully');
  } catch (e) {
    toast(e.message);
  }
}

function showReceipt(o) {
  let items = [];
  try { items = JSON.parse(o.itemsJson || '[]'); } catch {}
  $('receiptContent').innerHTML = `<p>Order #${1000 + o.id}</p><p>${new Date(o.createdAt).toLocaleString()}</p><p>${esc(o.customerName)} · ${esc(o.orderType)}${o.orderType === 'Dine-In' ? ' · Table ' + esc(o.tableNumber) : ''}</p><p><span class="status-pill">${o.status}</span></p><table class="receipt-table"><thead><tr><th>ITEM</th><th>QTY</th><th>PRICE</th><th>AMOUNT</th></tr></thead><tbody>${items.map(i => `<tr><td>${esc(i.name)}</td><td>${i.quantity}</td><td>${money(i.price)}</td><td>${money(i.price * i.quantity)}</td></tr>`).join('')}</tbody></table><div class="totals"><div><span>Subtotal</span><span>${money(o.subtotal)}</span></div><div><span>Discount</span><span>− ${money(o.discount)}</span></div><div><span>Tax (5%)</span><span>${money(o.tax)}</span></div><strong><span>Total</span><span>${money(o.total)}</span></strong></div><p>Thank you for dining with us!</p>`;
  $('receiptModal').classList.remove('hidden');
}

function closeReceipt() {
  $('receiptModal').classList.add('hidden');
}

function renderLatest(o) {
  const flow = ['PLACED', 'RECEIVED', 'IN-PROGRESS', 'COMPLETED'];
  $('latest').classList.remove('hidden');
  $('latest').innerHTML = `<div><p class="eyebrow">YOUR LATEST ORDER</p><b>Order #${1000 + o.id}</b> · <span class="status-pill ${o.status}">${o.status}</span><div class="status-track">${flow.map(s => `<span class="${flow.indexOf(s) <= flow.indexOf(o.status) ? 'current' : ''}">${s}</span>`).join('')}</div></div><button class="outline" onclick="openOrderReceipt(${o.id})">View receipt</button>`;
}

async function openOrderReceipt(id) {
  try {
    showReceipt(await api('/orders/' + id));
  } catch (e) {
    toast(e.message);
  }
}

function getHashView() {
  const h = (window.location.hash || '').replace('#', '').toLowerCase();
  return ['customer', 'staff', 'admin'].includes(h) ? h : null;
}

function showView(v, updateHash = true) {
  if (!['customer', 'staff', 'admin'].includes(v)) v = 'customer';
  ['customer', 'staff', 'admin'].forEach(k => $(k + 'View').classList.toggle('hidden', k !== v));
  document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.view === v));

  if (updateHash) {
    if (v === 'customer' && !window.location.hash) {
      // Keep URL clean on default view
    } else if (window.location.hash !== '#' + v) {
      window.location.hash = v;
    }
  }

  if (v === 'staff') {
    if (staffLogged) {
      $('staffLogin').classList.add('hidden');
      $('staffBoard').classList.remove('hidden');
      loadOrders();
    } else {
      $('staffLogin').classList.remove('hidden');
      $('staffBoard').classList.add('hidden');
    }
  }

  if (v === 'admin') {
    if (adminLogged) {
      $('adminLogin').classList.add('hidden');
      $('adminBoard').classList.remove('hidden');
      loadAdmin();
    } else {
      $('adminLogin').classList.remove('hidden');
      $('adminBoard').classList.add('hidden');
    }
  }
}

async function loginStaff() {
  $('staffError').textContent = '';
  const pin = $('staffPin').value;
  if (!pin) {
    $('staffError').textContent = 'Please enter staff PIN';
    return;
  }
  try {
    const res = await api('/staff/login', {
      method: 'POST',
      body: JSON.stringify({ pin })
    });
    if (res.token) {
      localStorage.setItem('dineflow_token', res.token);
    }
    staffLogged = true;
    adminLogged = false;
    $('staffPin').value = '';
    $('staffLogin').classList.add('hidden');
    $('staffBoard').classList.remove('hidden');
    window.location.hash = 'staff';
    loadOrders();
    toast('Logged in as Staff');
  } catch (e) {
    $('staffError').textContent = e.message;
  }
}

async function loginAdmin() {
  $('adminError').textContent = '';
  const password = $('adminPassword').value;
  if (!password) {
    $('adminError').textContent = 'Please enter admin password';
    return;
  }
  try {
    const res = await api('/admin/login', {
      method: 'POST',
      body: JSON.stringify({ password })
    });
    if (res.token) {
      localStorage.setItem('dineflow_token', res.token);
    }
    adminLogged = true;
    staffLogged = false;
    $('adminPassword').value = '';
    $('adminLogin').classList.add('hidden');
    $('adminBoard').classList.remove('hidden');
    window.location.hash = 'admin';
    loadAdmin();
    toast('Logged in as Admin');
  } catch (e) {
    $('adminError').textContent = e.message;
  }
}

async function logout(role) {
  try {
    await api('/auth/logout', { method: 'POST' });
  } catch (e) {}
  localStorage.removeItem('dineflow_token');
  if (role === 'staff') {
    staffLogged = false;
    $('staffBoard').classList.add('hidden');
    $('staffLogin').classList.remove('hidden');
    $('staffPin').value = '';
    $('staffError').textContent = '';
  } else {
    adminLogged = false;
    $('adminBoard').classList.add('hidden');
    $('adminLogin').classList.remove('hidden');
    $('adminPassword').value = '';
    $('adminError').textContent = '';
  }
  toast('Logged out');
}

async function loadOrders() {
  try {
    const os = await api('/orders');
    const statuses = ['ALL', 'PLACED', 'RECEIVED', 'IN-PROGRESS', 'COMPLETED'];
    $('statusFilters').innerHTML = statuses.map(s => `<button class="${staffFilter === s ? 'selected' : ''}" onclick="staffFilter='${s}';loadOrders()">${s} ${s === 'ALL' ? os.length : os.filter(o => o.status === s).length}</button>`).join('');
    const list = os.filter(o => staffFilter === 'ALL' || o.status === staffFilter);
    $('ordersGrid').innerHTML = list.length ? list.map(o => {
      let items = [];
      try { items = JSON.parse(o.itemsJson || '[]'); } catch {}
      const next = { PLACED: 'RECEIVED', RECEIVED: 'IN-PROGRESS', 'IN-PROGRESS': 'COMPLETED', COMPLETED: null }[o.status];
      return `<article class="order-card"><div class="order-head"><span>#${1000 + o.id}</span><span class="status-pill ${o.status}">${o.status}</span></div><div class="order-meta"><b>${esc(o.customerName)}</b> · ${esc(o.orderType)}<br>${o.orderType === 'Dine-In' ? 'Table: ' + esc(o.tableNumber) : ''}<br>${new Date(o.createdAt).toLocaleString()}</div><div class="order-items">${items.map(i => `<div class="order-item"><span>${esc(i.name)} × ${i.quantity}</span><b>${money(i.price * i.quantity)}</b></div>`).join('')}</div><div class="order-total"><span>Total</span><span>${money(o.total)}</span></div>${next ? `<button class="primary" onclick="advanceOrder(${o.id},'${next}')">${({ RECEIVED: 'Mark received', 'IN-PROGRESS': 'Start preparing', COMPLETED: 'Mark completed' })[next]}</button>` : '<button class="primary" disabled>Order completed</button>'}</article>`;
    }).join('') : '<p>No orders in this filter.</p>';
  } catch (e) {
    toast(e.message);
  }
}

async function advanceOrder(id, status) {
  try {
    await api('/orders/' + id + '/status', { method: 'PUT', body: JSON.stringify({ status }) });
    toast('Order #' + (1000 + id) + ' updated');
    loadOrders();
    if (lastOrder && lastOrder.id === id) {
      lastOrder.status = status;
      renderLatest(lastOrder);
    }
  } catch (e) {
    toast(e.message);
  }
}

async function addDish(e) {
  e.preventDefault();
  const file = $('dishImage').files[0];
  if (file && file.size > 400 * 1024) {
    toast('Image must be smaller than 400 KB');
    return;
  }
  let imageData = '';
  if (file) {
    imageData = await new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.onerror = reject;
      r.readAsDataURL(file);
    });
  }
  try {
    await api('/menu', {
      method: 'POST',
      body: JSON.stringify({
        name: $('dishName').value.trim(),
        category: $('dishCategory').value.trim(),
        price: Number($('dishPrice').value),
        emoji: $('dishEmoji').value || '🍽️',
        imageData,
        published: true
      })
    });
    e.target.reset();
    $('dishEmoji').value = '🍽️';
    toast('Dish added');
    loadMenu();
    loadAdmin();
  } catch (err) {
    toast(err.message);
  }
}

async function saveDish(id) {
  const price = Number($('price-' + id).value), published = $('published-' + id).checked;
  if (price < 0 || !Number.isFinite(price)) {
    toast('Enter a valid price');
    return;
  }
  try {
    await api('/menu/' + id, { method: 'PUT', body: JSON.stringify({ price, published }) });
    toast('Dish saved');
    loadMenu();
    loadAdmin();
  } catch (e) {
    toast(e.message);
  }
}

async function removeDish(id) {
  if (!confirm('Remove this dish from the menu?')) return;
  try {
    await api('/menu/' + id, { method: 'DELETE' });
    toast('Dish removed');
    loadMenu();
    loadAdmin();
  } catch (e) {
    toast(e.message);
  }
}

async function loadAdmin() {
  try {
    const [all, stats] = await Promise.all([api('/menu'), api('/stats')]);
    $('stats').innerHTML = [
      ['TOTAL DISHES', stats.dishes],
      ['PUBLISHED', stats.published],
      ['TOTAL ORDERS', stats.orders],
      ['TOTAL REVENUE', money(stats.revenue)]
    ].map(([a, b]) => `<div class="stat"><span>${a}</span><strong>${b}</strong></div>`).join('');

    $('adminMenu').innerHTML = '<div class="admin-row admin-header"><span></span><span>NAME</span><span>CATEGORY</span><span>PRICE (₹)</span><span>SAVE</span><span>PUBLISHED</span><span>REMOVE</span></div>' +
      all.map(i => `<div class="admin-row"><div class="admin-emoji">${i.imageData ? `<img src="${i.imageData}" style="width:100%;height:100%;object-fit:cover;border-radius:10px">` : esc(i.emoji || '🍽️')}</div><div class="admin-name">${esc(i.name)}</div><div>${esc(i.category)}</div><input id="price-${i.id}" type="number" min="0" step=".01" value="${i.price}"><button class="save-btn" onclick="saveDish(${i.id})">Save</button><label class="published-control"><input id="published-${i.id}" type="checkbox" ${i.published ? 'checked' : ''}><span>${i.published ? 'Published' : 'Hidden'}</span></label><button class="remove-btn" onclick="removeDish(${i.id})">Remove</button></div>`).join('');
  } catch (e) {
    toast(e.message);
  }
}

window.addEventListener('hashchange', () => {
  const v = getHashView();
  if (v) showView(v, false);
});

const staffPinInput = $('staffPin');
if (staffPinInput) {
  staffPinInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') loginStaff();
  });
}
const adminPassInput = $('adminPassword');
if (adminPassInput) {
  adminPassInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') loginAdmin();
  });
}

async function initApp() {
  loadMenu();

  const token = localStorage.getItem('dineflow_token');
  const hashView = getHashView();

  if (token) {
    try {
      const me = await api('/auth/me');
      if (me && (me.authenticated || me.ok)) {
        const role = String(me.role || '').toUpperCase();
        if (role === 'ADMIN') {
          adminLogged = true;
          staffLogged = false;
          $('adminLogin').classList.add('hidden');
          $('adminBoard').classList.remove('hidden');
        } else if (role === 'STAFF') {
          staffLogged = true;
          adminLogged = false;
          $('staffLogin').classList.add('hidden');
          $('staffBoard').classList.remove('hidden');
        }
      } else {
        localStorage.removeItem('dineflow_token');
      }
    } catch (err) {
      localStorage.removeItem('dineflow_token');
      staffLogged = false;
      adminLogged = false;
    }
  }

  let initialView = 'customer';
  if (hashView) {
    initialView = hashView;
  } else if (adminLogged) {
    initialView = 'admin';
  } else if (staffLogged) {
    initialView = 'staff';
  }

  showView(initialView, Boolean(hashView || adminLogged || staffLogged));
}

initApp();
