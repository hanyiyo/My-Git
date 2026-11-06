import { defineStore } from 'pinia'
import { api, setAccessToken } from './api'
export interface Profile { id: string; identifier: string; roles: string[]; enabled: boolean; credentialStatus: string | null }
interface Tokens { accessToken: string; account: Profile }
export const useSession = defineStore('session', {
  state: () => ({ account: null as Profile | null, restored: false }),
  actions: {
    async login(identifier: string, password: string, challengeId: string, captcha: string) {
      const result = await api<Tokens>('/auth/login', 'POST', { identifier, password, challengeId, captcha })
      setAccessToken(result.accessToken); this.account = result.account; this.restored = true
    },
    async restore() {
      if (this.restored) return
      try { const result = await api<Tokens>('/auth/refresh', 'POST'); setAccessToken(result.accessToken); this.account = result.account }
      catch { setAccessToken(''); this.account = null }
      this.restored = true
    },
    async logout() { await api('/auth/logout', 'POST'); setAccessToken(''); this.account = null }
  }
})
