export const AUTH_TOKEN_KEY = 'authToken';
export const USER_KEY = 'user';

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

export const saveAuth = ({ token, id, username, email }) => {
  localStorage.setItem(AUTH_TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify({ id, username, email }));
};

export const updateStoredUser = ({ id, username, email }) => {
  localStorage.setItem(USER_KEY, JSON.stringify({ id, username, email }));
};

export const clearAuth = () => {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};
