export const AUTH_TOKEN_KEY = 'authToken';
export const USER_KEY = 'user';
export const USER_ROLE = 'USER';
export const PUBLIC_ACCESS_ERROR = '仅支持普通用户登录，请使用普通用户账号。';

export const getAuthToken = () => localStorage.getItem(AUTH_TOKEN_KEY);

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
  localStorage.setItem(AUTH_TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify({ id, username, email, role }));
};

export const updateStoredUser = ({ id, username, email, role }) => {
  localStorage.setItem(USER_KEY, JSON.stringify({ id, username, email, role }));
};

export const clearAuth = () => {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};
