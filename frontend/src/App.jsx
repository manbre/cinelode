import { useEffect, useState } from 'react'
import LoginScreen from './components/LoginScreen'
import StartupLoader from './components/StartupLoader'
import Home from './pages/Home'
import { isNativeWebView } from './utils/runtime'
import { devLogin, getSession, login, logout } from './utils/auth'

function App() {
  const devLoginEnabled = import.meta.env.VITE_CINELODE_DEV_LOGIN
  const [session, setSession] = useState(null)
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState('')
  const [startupReady, setStartupReady] = useState(false)
  const [loaderFinished, setLoaderFinished] = useState(false)
  const nativeLoaderVersion = isNativeWebView()
    && new URLSearchParams(window.location.search).get('cinelodeNativeLoader')
  const hasNativeLoader = nativeLoaderVersion === '1' || nativeLoaderVersion === '2'
  const waitsForNativeLoader = nativeLoaderVersion === '2'
  const loading = status === 'loading' || (status === 'authenticated' && !startupReady)

  useEffect(() => {
    if (!waitsForNativeLoader) return
    const finishLoader = () => setLoaderFinished(true)
    window.addEventListener('cinelode-startup-loader-finished', finishLoader)
    return () => window.removeEventListener('cinelode-startup-loader-finished', finishLoader)
  }, [waitsForNativeLoader])

  useEffect(() => {
    if (hasNativeLoader) {
      window.ReactNativeWebView.postMessage(JSON.stringify({
        type: 'cinelode-startup-loading',
        loading,
      }))
    }
  }, [hasNativeLoader, loading])

  useEffect(() => {
    let active = true
    getSession()
      .then((currentSession) => {
        return currentSession ?? (devLoginEnabled ? devLogin() : null)
      })
      .then((currentSession) => {
        if (!active) return
        setSession(currentSession)
        setStatus(currentSession ? 'authenticated' : 'anonymous')
      })
      .catch((cause) => {
        if (active) {
          setError(cause.message)
          setStatus('error')
        }
      })

    return () => {
      active = false
    }
  }, [devLoginEnabled])

  const handleLogin = async (username, password) => {
    const authenticatedSession = await login(username, password)
    setLoaderFinished(false)
    setSession(authenticatedSession)
    setStatus('authenticated')
  }

  const handleLogout = async () => {
    await logout()
    setSession(null)
    setStartupReady(false)
    setStatus('anonymous')
  }

  let content = null
  if (status === 'error') {
    content = (
      <main className="flex min-h-dvh items-center justify-center bg-[#101011] px-5 text-center text-sm text-red-300">
        {error}
      </main>
    )
  } else if (status === 'anonymous') {
    content = <LoginScreen onLogin={handleLogin} error={error} />
  } else if (session) {
    content = (
      <Home
        onLogout={devLoginEnabled ? undefined : handleLogout}
        startupReady={startupReady && (nativeLoaderVersion === '1' || loaderFinished)}
        setStartupReady={setStartupReady}
      />
    )
  }

  return (
    <>
      {content}
      {!hasNativeLoader && (loading || !loaderFinished) && (
        <StartupLoader onMinimumElapsed={setLoaderFinished} />
      )}
    </>
  )
}

export default App
