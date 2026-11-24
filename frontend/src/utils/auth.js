async function getCsrfToken(fetchImpl, signal) {
  const response = await fetchImpl('/api/auth/csrf', {
    credentials: 'same-origin',
    signal,
  })
  if (!response.ok) {
    throw new Error('Security token could not be loaded.')
  }

  const csrf = await response.json()
  if (!csrf.token || !csrf.headerName) {
    throw new Error('The backend did not provide a valid security token.')
  }
  return csrf
}

export async function postJson(url, body, fetchImpl = fetch) {
  const csrf = await getCsrfToken(fetchImpl)
  return fetchImpl(url, {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(body),
  })
}

export async function getSession(fetchImpl = fetch, signal) {
  const response = await fetchImpl('/api/auth/session', {
    credentials: 'same-origin',
    signal,
  })
  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    throw new Error('Could not check the sign-in status.')
  }
  return response.json()
}

export async function login(username, password, fetchImpl = fetch) {
  const csrf = await getCsrfToken(fetchImpl)
  const response = await fetchImpl('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ username, password }),
  })

  if (response.status === 401) {
    throw new Error('Username or password is incorrect.')
  }
  if (response.status === 429) {
    throw new Error('Too many sign-in attempts. Please try again later.')
  }
  if (!response.ok) {
    throw new Error('Sign-in failed.')
  }

  return getSession(fetchImpl)
}

export async function devLogin(fetchImpl = fetch) {
  const csrf = await getCsrfToken(fetchImpl)
  const response = await fetchImpl('/api/auth/dev-login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      [csrf.headerName]: csrf.token,
    },
  })

  if (!response.ok) {
    throw new Error('Lokale Entwicklungsanmeldung fehlgeschlagen.')
  }

  return getSession(fetchImpl)
}

export async function logout(fetchImpl = fetch) {
  const csrf = await getCsrfToken(fetchImpl)
  const response = await fetchImpl('/api/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      [csrf.headerName]: csrf.token,
    },
  })
  if (!response.ok) {
    throw new Error('Abmeldung fehlgeschlagen.')
  }
}
