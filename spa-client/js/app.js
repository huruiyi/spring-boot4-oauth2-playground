const RESOURCE_SERVER = 'http://localhost:9001';

function login() {
  OAuth2PKCE.startAuthorization();
}

function logout() {
  OAuth2PKCE.logout();
}



async function callResourceServer() {
  const token = OAuth2PKCE.getAccessToken();
  if (!token) {
    showError('无有效令牌，请重新登录');
    return;
  }

  const responseEl = document.getElementById('resource-response');
  responseEl.textContent = '请求中...';

  try {
    const [infoRes, msgRes] = await Promise.all([
      fetch(`${RESOURCE_SERVER}/api/user/info`, { headers: { 'Authorization': `Bearer ${token}` } }),
      fetch(`${RESOURCE_SERVER}/api/user/messages`, { headers: { 'Authorization': `Bearer ${token}` } })
    ]);

    const info = infoRes.ok ? await infoRes.json() : { error: infoRes.statusText };
    const messages = msgRes.ok ? await msgRes.json() : { error: msgRes.statusText };

    responseEl.textContent = JSON.stringify({ userInfo: info, messages }, null, 2);
  } catch (e) {
    responseEl.textContent = '请求失败: ' + e.message;
  }
}

function displayToken(token) {
  document.getElementById('access-token').textContent = token;
  const claims = OAuth2PKCE.parseJwt(token);
  if (claims) {
    const userInfoEl = document.getElementById('user-info');
    userInfoEl.innerHTML = '';
    const fields = ['sub', 'preferred_username', 'nickname', 'email', 'phone', 'scope', 'roles', 'iss', 'exp', 'iat'];
    fields.forEach(field => {
      if (claims[field] !== undefined) {
        let value = claims[field];
        if (field === 'exp' || field === 'iat') {
          value = new Date(value * 1000).toLocaleString();
        }
        if (Array.isArray(value)) {
          value = value.join(', ');
        }
        const row = document.createElement('div');
        row.className = 'info-row';
        row.innerHTML = `<span class="info-label">${field}</span><span>${value}</span>`;
        userInfoEl.appendChild(row);
      }
    });
  }
}

function showError(msg) {
  const el = document.getElementById('error-msg');
  el.textContent = msg;
  el.classList.remove('hidden');
  setTimeout(() => el.classList.add('hidden'), 5000);
}

function init() {
  if (OAuth2PKCE.isAuthenticated()) {
    document.getElementById('login-section').classList.add('hidden');
    document.getElementById('authenticated-section').classList.remove('hidden');
    displayToken(OAuth2PKCE.getAccessToken());
  } else {
    document.getElementById('login-section').classList.remove('hidden');
    document.getElementById('authenticated-section').classList.add('hidden');
  }
}

init();
