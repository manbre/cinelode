
import { useCallback, useEffect, useRef, useState } from 'react'
import LoginScreen from './components/LoginScreen'
import Home from './pages/Home'
import { devLogin, getSession, login, logout } from './utils/auth'

const INTRO_FALLBACK_MS = 5000

function App() {
  const devLoginEnabled = import.meta.env.VITE_CINELODE_DEV_LOGIN
  const [session, setSession] = useState(null)
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState('')
  const [introVisible, setIntroVisible] = useState(true)
  const [introFading, setIntroFading] = useState(false)
  const introFadingRef = useRef(false)
  const introExitTimer = useRef(null)

  const finishIntro = useCallback(() => {
    if (introFadingRef.current) {
      return
    }
    introFadingRef.current = true
    setIntroFading(true)
    introExitTimer.current = window.setTimeout(() => {
      setIntroVisible(false)
      introExitTimer.current = null
    }, 500)
  }, [])

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      finishIntro()
    }, INTRO_FALLBACK_MS)
    return () => {
      window.clearTimeout(timeout)
      if (introExitTimer.current) {
        window.clearTimeout(introExitTimer.current)
      }
    }
  }, [finishIntro])

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
    setSession(authenticatedSession)
    setStatus('authenticated')
  }

  const handleLogout = async () => {
    await logout()
    setSession(null)
    setStatus('anonymous')
  }

  let content
  if (status === 'loading') {
    content = (
      <main className="flex min-h-dvh items-center justify-center bg-[#101011] text-sm text-white/60">
        Loading Cinelode …
      </main>
    )
  } else if (status === 'error') {
    content = (
      <main className="flex min-h-dvh items-center justify-center bg-[#101011] px-5 text-center text-sm text-red-300">
        {error}
      </main>
    )
  } else if (!session) {
    content = <LoginScreen onLogin={handleLogin} error={error} />
  } else {
    content = <Home onLogout={devLoginEnabled ? undefined : handleLogout} />
  }

  return (
    <>
      {content}
      {introVisible && (
        <div
          aria-hidden="true"
          className={`pointer-events-none fixed -inset-[2px] z-[100] overflow-hidden bg-black transition-opacity duration-500 ${
            introFading ? 'opacity-0' : 'opacity-100'
          }`}
        >
          <video
            src="/Cinelode_intro.mp4"
            autoPlay
            muted
            playsInline
            tabIndex={-1}
            onEnded={finishIntro}
            onError={() => {
              console.warn('Cinelode: intro video could not be loaded')
              finishIntro()
            }}
            className="absolute inset-0 size-full bg-black object-cover"
          />
        </div>
      )}
    </>
  )
}

export default App
