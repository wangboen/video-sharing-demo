export const AUTH_TOKEN_KEY = 'authToken';
export const USER_KEY = 'user';
export const USER_ROLE = 'USER';
export const PUBLIC_ACCESS_ERROR = '仅支持普通用户登录，请使用普通用户账号。';

let accessToken = null;

export const getAuthToken = () => accessToken;
export const setAuthToken = (token) => { accessToken = token || null; };

export const getStoredUser = () => {
  try {
    const storedUser = localStorage.getItem(USER_KEY);
    return storedUser ? JSON.parse(storedUser) : null;
  } catch {
    clearAuth();
    return null;
  }
};

export const saveAuth = ({ token, id, username, email, role }) => {
  setAuthToken(token);
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.setItem(USER_KEY, JSON.stringify({ id, username, email, role }));
};

export const updateStoredUser = ({ id, username, email, role }) => {
  localStorage.setItem(USER_KEY, JSON.stringify({ id, username, email, role }));
};

export const clearAuth = () => {
  setAuthToken(null);
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

// Remove access tokens persisted by versions before refresh-cookie sessions.
localStorage.removeItem(AUTH_TOKEN_KEY);
