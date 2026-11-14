import { useState } from 'react'

function LoginScreen({ onLogin, error: initialError = '' }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(initialError)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await onLogin(username, password)
    } catch (cause) {
      setError(cause.message || 'Sign-in failed.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center bg-[#101011] px-5 text-white">
      <form
        onSubmit={submit}
        className="w-full max-w-sm space-y-5 rounded-2xl border border-white/10 bg-white/[0.04] p-7 shadow-2xl"
      >
        <div>
          <h1 className="text-2xl font-semibold">Cinelode</h1>
          <p className="mt-2 text-sm text-white/60">Sign in to open your library.</p>
        </div>
        <label className="block space-y-2 text-sm">
          <span>Username</span>
          <input
            autoComplete="username"
            autoFocus
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className="min-h-12 w-full rounded-lg border border-white/15 bg-black/30 px-3 py-2.5 outline-none focus:border-accent"
          />
        </label>
        <label className="block space-y-2 text-sm">
          <span>Password</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="min-h-12 w-full rounded-lg border border-white/15 bg-black/30 px-3 py-2.5 outline-none focus:border-accent"
          />
        </label>
        {error && <p role="alert" className="text-sm text-red-300">{error}</p>}
        <button
          type="submit"
          disabled={submitting}
          className="min-h-12 w-full rounded-lg bg-accent px-4 py-2.5 font-semibold text-black transition hover:bg-accent-hover disabled:cursor-wait disabled:opacity-60"
        >
          {submitting ? 'Signing in …' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}

export default LoginScreen
