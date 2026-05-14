// ===== CarSpot.lk — Shared Utilities =====

async function api(method, url, body = null) {
    const opts = { method, headers: { 'Content-Type': 'application/json' }, credentials: 'same-origin' };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    const data = await res.json().catch(() => ({}));
    return { ok: res.ok, status: res.status, data };
}

let currentUser = null;
let currentRole = null;

async function checkAuth() {
    const { ok, data } = await api('GET', '/api/auth/status');
    if (ok && data.authenticated) {
        currentRole = data.role;
        currentUser = data.user || data.admin || data.seller || null;
        return true;
    }
    currentRole = null; currentUser = null; return false;
}

async function requireAuth(redirectTo = 'signin.html') {
    const authed = await checkAuth();
    if (!authed) window.location.href = redirectTo;
    return authed;
}

async function requireAdmin() {
    const authed = await checkAuth();
    if (!authed || !['ADMIN','SUPERADMIN'].includes(currentRole?.toUpperCase())) {
        window.location.href = 'signin.html'; return false;
    }
    return true;
}

// Role badge helper
function roleBadge(role) {
    if (!role) return '';
    const map = {
        'BUYER':      { bg:'#dbeafe', color:'#1e40af', icon:'🛒', label:'Buyer' },
        'SELLER':     { bg:'#d1fae5', color:'#065f46', icon:'🏷',  label:'Seller' },
        'DEALER':     { bg:'#ede9fe', color:'#4c1d95', icon:'🏢',  label:'Dealer' },
        'ADMIN':      { bg:'#fee2e2', color:'#991b1b', icon:'⚙️',  label:'Admin' },
        'SUPERADMIN': { bg:'#fce7f3', color:'#9d174d', icon:'👑',  label:'Super Admin' },
    };
    const r = map[role.toUpperCase()] || { bg:'#f1f5f9', color:'#475569', icon:'👤', label: role };
    return `<span style="background:${r.bg};color:${r.color};padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;display:inline-flex;align-items:center;gap:4px">${r.icon} ${r.label}</span>`;
}

function renderNavbar(activePage = '') {
    const nav = document.getElementById('navbar');
    if (!nav) return;
    const brandName = 'Car<span style="color:var(--accent)">Spot</span>.lk';
    let links = '';
    if (currentRole) {
        const role = currentRole.toUpperCase();
        const name = currentUser?.name || currentUser?.adminId || 'User';
        const badge = roleBadge(role);
        if (['ADMIN','SUPERADMIN'].includes(role)) {
            links = `<a href="admin-dashboard.html">Dashboard</a>
                     <a href="browse.html">Browse Cars</a>
                     <span class="nav-user-chip">${badge}&nbsp;${name}</span>
                     <a href="#" onclick="logout()">Sign Out</a>`;
        } else if (['SELLER','DEALER'].includes(role)) {
            links = `<a href="browse.html">Browse Cars</a>
                     <a href="sellers.html">Users</a>
                     <a href="seller-dashboard.html">My Account</a>
                     <span class="nav-user-chip">${badge}&nbsp;${name}</span>
                     <a href="#" onclick="logout()">Sign Out</a>`;
        } else {
            links = `<a href="browse.html">Browse Cars</a>
                     <a href="sellers.html">Users</a>
                     <a href="account.html">My Account</a>
                     <span class="nav-user-chip">${badge}&nbsp;${name}</span>
                     <a href="#" onclick="logout()">Sign Out</a>`;
        }
    } else {
        links = `<a href="browse.html">Browse Cars</a>
                 <a href="sellers.html">Users</a>
                 <a href="signin.html" class="${activePage==='signin'?'btn-nav-accent':''}">Sign In</a>
                 <a href="register.html" class="btn-nav-accent">Register Free</a>`;
    }
    nav.innerHTML = `<a href="index.html" class="logo">${brandName}</a><div class="nav-links">${links}</div>`;
}

async function logout() {
    await api('POST', '/api/auth/logout');
    currentRole = null; currentUser = null;
    window.location.href = 'index.html';
}

function showAlert(containerId, message, type = 'error') {
    const el = document.getElementById(containerId);
    if (!el) return;
    const icons = { error:'❌', success:'✅', info:'ℹ️', warning:'⚠️' };
    el.innerHTML = `<div class="alert alert-${type}">${icons[type]||''} ${message}</div>`;
    el.scrollIntoView({ behavior:'smooth', block:'nearest' });
    setTimeout(() => { if (el) el.innerHTML = ''; }, 6000);
}

function renderStars(rating) {
    const r = Math.max(0, Math.min(5, Math.round(rating)));
    return '★'.repeat(r) + '☆'.repeat(5 - r);
}

function statusBadge(status) {
    return `<span class="badge badge-${(status||'').toLowerCase()}">${status||''}</span>`;
}

function formatPrice(price) {
    return 'Rs. ' + Number(price).toLocaleString('en-LK');
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    try { return new Date(dateStr).toLocaleDateString('en-LK', { year:'numeric', month:'short', day:'numeric' }); }
    catch { return ''; }
}

const BRAND = 'CarSpot.lk';
const BRAND_FULL = '🚗 CarSpot.lk';
