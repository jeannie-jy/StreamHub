/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_WS_BASE_URL?: string
  readonly VITE_DEV_API_TARGET?: string
  readonly VITE_DEV_REALTIME_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
