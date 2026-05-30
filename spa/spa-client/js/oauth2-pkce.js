const OAuth2PKCE = (() => {
  const AUTH_SERVER = 'http://localhost:9100';
  const RESOURCE_SERVER = 'http://localhost:9200';
  const CLIENT_ID = 'spa-client';
  const REDIRECT_URI = window.location.origin + '/callback.html';
  const SILENT_REDIRECT_URI = window.location.origin + '/silent-refresh.html';
  const SCOPES = 'openid profile read write';
  const INTROSPECT_CLIENT_ID = 'oidc-client';
  const INTROSPECT_CLIENT_SECRET = 'secret';

  function generateRandomString(length) {
    const array = new Uint8Array(length);
    crypto.getRandomValues(array);
    return Array.from(array, b => b.toString(16).padStart(2, '0')).join('');
  }

  async function sha256(plain) {
    const encoder = new TextEncoder();
    const data = encoder.encode(plain);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return base64urlencode(digest);
  }

  function base64urlencode(buffer) {
    const bytes = new Uint8Array(buffer);
    let str = '';
    bytes.forEach(b => str += String.fromCharCode(b));
    return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  function base64urldecode(str) {
    str = str.replace(/-/g, '+').replace(/_/g, '/');
    while (str.length % 4) str += '=';
    const binary = atob(str);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return new TextDecoder('utf-8').decode(bytes);
  }

  function parseJwt(token) {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const payload = base64urldecode(parts[1]);
      return JSON.parse(payload);
    } catch {
      return null;
    }
  }

  async function startAuthorization() {
    const codeVerifier = generateRandomString(32);
    const codeChallenge = await sha256(codeVerifier);
    sessionStorage.setItem('pkce_code_verifier', codeVerifier);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: CLIENT_ID,
      redirect_uri: REDIRECT_URI,
      scope: SCOPES,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256'
    });

    window.location.href = `${AUTH_SERVER}/oauth2/authorize?${params.toString()}`;
  }

  async function exchangeCode(code) {
    const codeVerifier = sessionStorage.getItem('pkce_code_verifier');
    if (!codeVerifier) {
      throw new Error('未找到 code_verifier，请重新登录');
    }
    sessionStorage.removeItem('pkce_code_verifier');

    const params = new URLSearchParams({
      grant_type: 'authorization_code',
      code: code,
      client_id: CLIENT_ID,
      redirect_uri: REDIRECT_URI,
      code_verifier: codeVerifier
    });

    const response = await fetch(`${AUTH_SERVER}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString()
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error_description || error.error || '令牌交换失败');
    }

    return response.json();
  }

  async function refreshToken() {
    const rt = sessionStorage.getItem('refresh_token');
    if (!rt) {
      throw new Error('无 refresh_token，请重新登录');
    }

    const params = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: rt,
      client_id: CLIENT_ID
    });

    const response = await fetch(`${AUTH_SERVER}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString()
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error_description || error.error || '刷新令牌失败');
    }

    const tokenResponse = await response.json();
    setTokens(tokenResponse);
    return tokenResponse;
  }

  function setTokens(tokenResponse) {
    sessionStorage.setItem('access_token', tokenResponse.access_token);
    if (tokenResponse.refresh_token) {
      sessionStorage.setItem('refresh_token', tokenResponse.refresh_token);
    }
    if (tokenResponse.id_token) {
      sessionStorage.setItem('id_token', tokenResponse.id_token);
    }
    sessionStorage.setItem('token_expires_at', String(Date.now() + tokenResponse.expires_in * 1000));
  }

  function startSilentRefresh() {
    const codeVerifier = generateRandomString(32);
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';

    sha256(codeVerifier).then((codeChallenge) => {
      sessionStorage.setItem('pkce_code_verifier', codeVerifier);
      const params = new URLSearchParams({
        response_type: 'code',
        client_id: CLIENT_ID,
        redirect_uri: SILENT_REDIRECT_URI,
        scope: SCOPES,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        prompt: 'none'
      });
      iframe.src = `${AUTH_SERVER}/oauth2/authorize?${params.toString()}`;
    });

    document.body.appendChild(iframe);

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        document.body.removeChild(iframe);
        reject(new Error('静默刷新超时'));
      }, 10000);

      window.addEventListener('message', function handler(e) {
        if (e.origin !== window.location.origin) return;
        if (e.data?.type !== 'oauth2-silent-refresh') return;
        clearTimeout(timer);
        document.body.removeChild(iframe);
        window.removeEventListener('message', handler);
        if (e.data.code) {
          exchangeCodeSilent(e.data.code).then(resolve).catch(reject);
        } else {
          reject(new Error('静默刷新失败: ' + (e.data.error || 'login_required')));
        }
      });
    });
  }

  async function exchangeCodeSilent(code) {
    const codeVerifier = sessionStorage.getItem('pkce_code_verifier');
    if (!codeVerifier) throw new Error('未找到 code_verifier');
    sessionStorage.removeItem('pkce_code_verifier');

    const params = new URLSearchParams({
      grant_type: 'authorization_code',
      code: code,
      client_id: CLIENT_ID,
      redirect_uri: SILENT_REDIRECT_URI,
      code_verifier: codeVerifier
    });

    const response = await fetch(`${AUTH_SERVER}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString()
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error_description || error.error || '令牌交换失败');
    }

    const tokenResponse = await response.json();
    setTokens(tokenResponse);
    return tokenResponse;
  }

  async function callResourceServer(endpoint) {
    const token = getAccessToken();
    if (!token) {
      clearTokens();
      throw new Error('Token 已过期，请重新登录');
    }

    const response = await fetch(`${RESOURCE_SERVER}${endpoint}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (response.status === 401) {
      clearTokens();
      throw new Error('401: 令牌无效或已过期，请重新登录');
    }

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    return response.json();
  }

  async function introspectToken(token, tokenTypeHint) {
    const basic = btoa(`${INTROSPECT_CLIENT_ID}:${INTROSPECT_CLIENT_SECRET}`);
    const params = new URLSearchParams({ token });
    if (tokenTypeHint) params.set('token_type_hint', tokenTypeHint);

    const response = await fetch(`${AUTH_SERVER}/oauth2/introspect`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': `Basic ${basic}`
      },
      body: params.toString()
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error_description || error.error || 'Introspection 失败');
    }

    return response.json();
  }

  async function revokeToken(token, tokenTypeHint) {
    const body = { token };
    if (tokenTypeHint) body.token_type_hint = tokenTypeHint;

    const response = await fetch(`${AUTH_SERVER}/api/revoke`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error_description || error.error || 'Revocation 失败');
    }
  }

  function getAccessToken() {
    return sessionStorage.getItem('access_token');
  }

  function getRefreshToken() {
    return sessionStorage.getItem('refresh_token');
  }

  function getIdToken() {
    return sessionStorage.getItem('id_token');
  }

  function getExpiresAtMs() {
    return Number(sessionStorage.getItem('token_expires_at')) || 0;
  }

  function getClaims() {
    const token = getAccessToken();
    return token ? parseJwt(token) : null;
  }

  function getScopes() {
    const claims = getClaims();
    const scopeStr = claims?.scp || claims?.scope || '';
    if (!scopeStr) return [];
    return typeof scopeStr === 'string' ? scopeStr.split(' ').filter(Boolean) : scopeStr;
  }

  function isAuthenticated() {
    const token = getAccessToken();
    if (!token) return false;
    const expiresAt = getExpiresAtMs();
    if (expiresAt && Date.now() > expiresAt) {
      clearTokens();
      return false;
    }
    return true;
  }

  function clearTokens() {
    sessionStorage.removeItem('access_token');
    sessionStorage.removeItem('refresh_token');
    sessionStorage.removeItem('id_token');
    sessionStorage.removeItem('token_expires_at');
    sessionStorage.removeItem('pkce_code_verifier');
  }

  function logout() {
    const idToken = getIdToken();
    clearTokens();

    const params = new URLSearchParams({
      client_id: CLIENT_ID,
      post_logout_redirect_uri: window.location.origin + '/'
    });
    if (idToken) {
      params.set('id_token_hint', idToken);
    }

    window.location.href = `${AUTH_SERVER}/connect/logout?${params.toString()}`;
  }

  return {
    startAuthorization,
    exchangeCode,
    refreshToken,
    startSilentRefresh,
    callResourceServer,
    introspectToken,
    revokeToken,
    getAccessToken,
    getRefreshToken,
    getIdToken,
    getExpiresAtMs,
    getClaims,
    getScopes,
    isAuthenticated,
    setTokens,
    clearTokens,
    logout,
    parseJwt,
    fmtDate,
    fmtRemaining,
    AUTH_SERVER,
    RESOURCE_SERVER
  };

  function fmtDate(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    if (isNaN(d.getTime())) return '-';
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }

  function fmtRemaining(ms) {
    const min = Math.floor(ms / 60000);
    const sec = Math.floor((ms % 60000) / 1000);
    if (min === 0) return `${sec}秒`;
    return `${min}分${sec}秒`;
  }
})();
