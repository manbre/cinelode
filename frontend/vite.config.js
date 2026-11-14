import { readFileSync } from 'node:fs'
import process from 'node:process'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

const isLanMode = process.env.CINELODE_DEV_LAN === 'true'
const isHttps = process.env.CINELODE_DEV_TLS_ENABLED === 'true'
const isDevLoginEnabled = process.env.CINELODE_DEV_LOGIN === 'true'
  && (!isLanMode || isHttps)

function readTlsFile(variable) {
  const file = process.env[variable]

  if (!file) {
    throw new Error(
      `${variable} must point to a TLS certificate file for LAN development.`,
    )
  }

  return readFileSync(file)
}

const serverOptions = isLanMode
  ? {
      host: '0.0.0.0',
      ...(isHttps
        ? {
            https: {
              key: readTlsFile('CINELODE_DEV_TLS_KEY'),
              cert: readTlsFile('CINELODE_DEV_TLS_CERT'),
            },
          }
        : {}),
    }
  : {
      host: '127.0.0.1',
    }

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    'import.meta.env.VITE_CINELODE_DEV_LOGIN': JSON.stringify(isDevLoginEnabled),
  },
  server: {
    ...serverOptions,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        xfwd: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyRequest, request) => {
            if (request.url?.startsWith('/api/auth/dev-login')) {
              proxyRequest.setHeader(
                'X-Cinelode-Dev-Login',
                isDevLoginEnabled ? 'true' : 'false',
              )
            }
          })
        },
      },
    },
  },
})
