import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as userApi from '@/api/user'
import { beginLogout, endLogout } from '@/api/http'
import type { TokenVO, User } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)
  const isAuthenticated = computed(() => !!token.value)
  function restore() {
    token.value = sessionStorage.getItem('token')
    const raw = sessionStorage.getItem('userInfo')
    if (!raw) { user.value = null; return }
    try { user.value = JSON.parse(raw) as User } catch { sessionStorage.removeItem('userInfo'); user.value = null }
  }
  function setSession(tokens: TokenVO, newUser?: User) {
    token.value = tokens.accessToken
    sessionStorage.setItem('token', tokens.accessToken)
    sessionStorage.setItem('refreshToken', tokens.refreshToken)
    sessionStorage.setItem('tokenType', tokens.tokenType)
    sessionStorage.setItem('accessTtl', String(tokens.accessTtl))
    if (newUser !== undefined) { user.value = newUser; sessionStorage.setItem('userInfo', JSON.stringify(newUser)) }
  }
  async function fetchCurrentUser() { user.value = await userApi.getCurrentUser(); sessionStorage.setItem('userInfo', JSON.stringify(user.value)); return user.value }
  function updateAvatar(icon: string) {
    if (!user.value) return
    user.value = { ...user.value, icon }
    sessionStorage.setItem('userInfo', JSON.stringify(user.value))
  }
  async function signIn(form: userApi.LoginForm) {
    const tokens = await userApi.login(form)
    setSession(tokens)
    try {
      return await fetchCurrentUser()
    } catch (error) {
      if ((error as { code?: number }).code === 401) throw error
      user.value = null
      sessionStorage.removeItem('userInfo')
      return null
    }
  }
  async function signOut() {
    const refreshToken = await beginLogout()
    try {
      if (refreshToken) await userApi.logout(refreshToken)
    } finally {
      clearSession()
      endLogout()
    }
  }
  function clearSession() {
    token.value = null
    user.value = null
    for (const key of ['token', 'refreshToken', 'tokenType', 'accessTtl', 'userInfo']) sessionStorage.removeItem(key)
  }
  restore()
  return { token, user, isAuthenticated, restore, setSession, fetchCurrentUser, updateAvatar, signIn, signOut, clearSession }
})
