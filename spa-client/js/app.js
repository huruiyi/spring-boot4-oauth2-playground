let autoRefreshEnabled = true;
let refreshing = false;
let countdownTimer = null;
let refreshTimer = null;

function $(id) { return document.getElementById(id); }

function init() {
  if (OAuth2PKCE.isAuthenticated()) {
    $('home-page').classList.add('hidden');
    $('profile-page').classList.remove('hidden');
    updateTokenStatus();
    startCountdown();
    if (autoRefreshEnabled) startAutoRefresh();
  } else {
    $('home-page').classList.remove('hidden');
    $('profile-page').classList.add('hidden');
  }
}

function updateTokenStatus() {
  const claims = OAuth2PKCE.getClaims() || {};
  const expMs = OAuth2PKCE.getExpiresAtMs();
  const now = Date.now();

  if (claims.iat) $('issued-at').textContent = OAuth2PKCE.fmtDate(claims.iat * 1000);
  if (expMs > 0) $('token-expiry').textContent = OAuth2PKCE.fmtDate(expMs);
  if (claims.iat) $('id-issued-at').textContent = OAuth2PKCE.fmtDate(claims.iat * 1000);
  if (claims.exp) $('id-expires-at').textContent = OAuth2PKCE.fmtDate(claims.exp * 1000);

  $('access-token-display').textContent = OAuth2PKCE.getAccessToken() || '';

  updateScopes();
  updateRefreshTokenRow();
  updateAutoRefreshRow();
  updateClaimsTable(claims);
  updateMfaStatus(claims);

  if (!expMs || expMs <= 0) {
    $('countdown').textContent = '';
    $('countdown').className = '';
    $('expires-soon').textContent = '-';
    $('expires-soon').className = 'info-value';
    return;
  }

  const remaining = expMs - now;

  if (remaining <= 0) {
    $('countdown').textContent = '已过期';
    $('countdown').className = 'cd-expired';
    $('expires-soon').textContent = '已过期';
    $('expires-soon').className = 'info-value text-danger';
    return;
  }

  $('countdown').textContent = OAuth2PKCE.fmtRemaining(remaining);
  $('countdown').className = remaining < 60000 ? 'cd-warn' : 'cd-ok';
  $('expires-soon').textContent = OAuth2PKCE.fmtRemaining(remaining) + ' 后过期';
  if (remaining < 60000) {
    $('expires-soon').className = 'info-value text-danger';
  } else if (remaining < 120000) {
    $('expires-soon').className = 'info-value text-warn';
  } else {
    $('expires-soon').className = 'info-value text-ok';
  }
}

function updateScopes() {
  const scopes = OAuth2PKCE.getScopes();
  const el = $('scopes-row');
  el.innerHTML = '';
  if (scopes.length === 0) {
    el.innerHTML = '<span class="text-muted">-</span>';
    return;
  }
  scopes.forEach(s => {
    const tag = document.createElement('span');
    tag.className = 'scope-tag';
    tag.textContent = s;
    el.appendChild(tag);
  });
}

function updateRefreshTokenRow() {
  const hasRt = !!OAuth2PKCE.getRefreshToken();
  const el = $('refresh-token-row');
  el.innerHTML =
    `<span class="status-dot ${hasRt ? 'dot-green' : 'dot-amber'}"></span>` +
    `<span class="${hasRt ? 'text-ok' : 'text-warn'}">${hasRt ? '有 (旋转)' : 'No'}</span>`;
}

function updateAutoRefreshRow() {
  const el = $('auto-refresh-row');
  el.innerHTML =
    `<span class="status-dot ${autoRefreshEnabled ? 'dot-green' : 'dot-gray'}"></span>` +
    `<span class="${autoRefreshEnabled ? 'text-ok' : 'text-muted'}">${autoRefreshEnabled ? '已开启' : '未开启'}</span>` +
    `<span class="text-muted meta">过期前60秒</span>`;
  $('btn-toggle-auto').textContent = autoRefreshEnabled ? '关闭自动续期' : '开启自动续期';
}

function updateClaimsTable(claims) {
  const el = $('claims-table');
  el.innerHTML = '';
  const fields = ['sub', 'preferred_username', 'nickname', 'email', 'phone', 'picture', 'scp', 'scope', 'roles', 'mfa_enabled', 'iss', 'aud', 'azp', 'jti', 'sid'];
  fields.forEach(key => {
    if (claims[key] === undefined) return;
    let value = claims[key];
    if (key === 'exp' || key === 'iat' || key === 'auth_time') value = OAuth2PKCE.fmtDate(value * 1000);
    if (Array.isArray(value)) value = value.join(', ');
    const isLong = typeof value === 'string' && value.length > 40;
    const row = document.createElement('div');
    row.className = 'info-row';
    row.innerHTML = `<div class="info-label">${key}</div><div class="info-value ${isLong ? 'mono-sm' : ''}">${value}</div>`;
    el.appendChild(row);
  });
}

function updateMfaStatus(claims) {
  const statusEl = $('mfa-status');
  const btnEl = $('mfa-btn');
  const hintEl = $('mfa-hint');
  if (!statusEl) return;
  
  const enabled = claims && claims.mfa_enabled === true;
  statusEl.innerHTML = `<span class="status-dot ${enabled ? 'dot-green' : 'dot-gray'}"></span>` +
    `<span class="${enabled ? 'text-ok' : 'text-muted'}">${enabled ? '已启用 (TOTP)' : '未启用'}</span>`;
  btnEl.textContent = enabled ? '⚙️ 管理 MFA 设置' : '🔐 启用 MFA';
  hintEl.textContent = enabled 
    ? 'MFA 已启用，登录时需要输入 Google Authenticator 生成的 6 位验证码。' 
    : '启用 MFA 后，登录时需要输入验证码，提高账户安全性。';
}

function startCountdown() {
  stopCountdown();
  countdownTimer = setInterval(updateTokenStatus, 1000);
}

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
}

function startAutoRefresh() {
  stopAutoRefresh();
  let lastRefreshTime = 0;
  refreshTimer = setInterval(() => {
    const rem = OAuth2PKCE.getExpiresAtMs() ? OAuth2PKCE.getExpiresAtMs() - Date.now() : 0;
    const now = Date.now();
    if (rem < 60000 && rem > 0 && !refreshing && (now - lastRefreshTime > 5000)) {
      lastRefreshTime = now;
      showAction(`[${new Date().toLocaleTimeString()}] 自动续期触发, remaining=${Math.round(rem / 1000)}s`);
      doRefresh();
    }
    if (rem <= 0) {
      OAuth2PKCE.clearTokens();
      window.location.href = '/';
    }
  }, 1000);
}

function stopAutoRefresh() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
}

function toggleAutoRefresh() {
  autoRefreshEnabled = !autoRefreshEnabled;
  if (autoRefreshEnabled) startAutoRefresh();
  else stopAutoRefresh();
  updateAutoRefreshRow();
}

async function doRefresh() {
  refreshing = true;
  $('btn-refresh').textContent = '⏳ 续期中...';
  $('btn-refresh').disabled = true;
  hideError();
  const hasRt = !!OAuth2PKCE.getRefreshToken();
  showAction(`[${new Date().toLocaleTimeString()}] 续期开始, refreshToken=${hasRt}`);
  try {
    if (hasRt) {
      await OAuth2PKCE.refreshToken();
      appendAction(' → refreshToken成功');
    } else {
      await OAuth2PKCE.startSilentRefresh();
      appendAction(' → silentRefresh成功');
    }
    updateTokenStatus();
  } catch (e) {
    showError('续期失败: ' + e.message);
    appendAction(' → 失败: ' + e.message);
  } finally {
    refreshing = false;
    $('btn-refresh').textContent = '🔄 手动续期';
    $('btn-refresh').disabled = false;
  }
}

function doLogout() {
  stopCountdown();
  stopAutoRefresh();
  OAuth2PKCE.logout();
}

async function callApi(endpoint) {
  const el = endpoint === '/api/user/info' ? $('user-info-resp') : $('messages-resp');
  el.textContent = '请求中...';
  try {
    const data = await OAuth2PKCE.callResourceServer(endpoint);
    el.textContent = JSON.stringify(data, null, 2);
  } catch (e) {
    el.textContent = '请求失败: ' + e.message;
    if (e.message.includes('401') || e.message.includes('过期')) {
      showError(e.message + '，请点击手动续期或重新登录');
    }
  }
}

async function doIntrospect(tokenTypeHint) {
  const token = tokenTypeHint === 'refresh_token' ? OAuth2PKCE.getRefreshToken() : OAuth2PKCE.getAccessToken();
  const el = $('introspect-result');
  if (!token) { el.textContent = `无 ${tokenTypeHint}`; return; }
  el.textContent = '请求中...';
  try {
    const data = await OAuth2PKCE.introspectToken(token, tokenTypeHint);
    el.textContent = JSON.stringify(data, null, 2);
  } catch (e) {
    el.textContent = '失败: ' + e.message;
  }
}

async function doRevoke(tokenTypeHint) {
  const token = tokenTypeHint === 'refresh_token' ? OAuth2PKCE.getRefreshToken() : OAuth2PKCE.getAccessToken();
  const el = $('revoke-result');
  if (!token) { el.textContent = `无 ${tokenTypeHint}`; return; }
  el.textContent = '请求中...';
  try {
    await OAuth2PKCE.revokeToken(token, tokenTypeHint);
    el.textContent = `✓ ${tokenTypeHint} 已吊销\n(HTTP 200, 无响应体)\n\nJWT 是无状态的，吊销不会立即使 resource-server 拒绝请求。`;
    stopCountdown();
    stopAutoRefresh();
    OAuth2PKCE.clearTokens();
    if ($('revoke-redirect').checked) {
      window.location.href = '/';
    } else {
      el.textContent += '\n已清除本地 token，请重新登录。';
    }
  } catch (e) {
    el.textContent = '失败: ' + e.message;
  }
}

function showError(msg) {
  const el = $('error-alert');
  el.textContent = msg;
  el.classList.add('visible');
}

function hideError() {
  $('error-alert').classList.remove('visible');
}

function showAction(msg) {
  const el = $('action-alert');
  el.textContent = msg;
  el.classList.add('visible');
}

function appendAction(msg) {
  const el = $('action-alert');
  el.textContent += msg;
}

init();
