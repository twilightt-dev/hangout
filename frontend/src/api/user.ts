import { dataOf, http } from './http'
import type { TokenVO, User, UserInfo, UserVO } from '@/types/api'

export type LoginType = 'code' | 'password'
export interface LoginForm {
  phone: string
  loginType: LoginType
  verifyCode?: string
  password?: string
}
export interface RegisterForm {
  phone: string
  verifyCode: string
  password: string
  confirmPassword: string
}

export const sendCode = (phone: string) => dataOf<void>(http.post('/user/login/code', null, { params: { phone } }))
export const sendRegisterCode = (phone: string) => dataOf<void>(http.post('/user/register/code', null, { params: { phone } }))
export const register = (form: RegisterForm) => dataOf<void>(http.post('/user/register', form))
export const login = (form: LoginForm) => dataOf<TokenVO>(http.post('/user/login', form))
export const logout = (refreshToken: string) => dataOf<void>(http.post('/user/logout', { refreshToken }))
export const getCurrentUser = () => dataOf<User>(http.get<User>('/user/me'))
export const getUser = (id: number) => dataOf<UserVO>(http.get<UserVO>(`/user/${id}`))
export const getUserInfo = (id: number) => dataOf<UserInfo>(http.get<UserInfo>(`/user/info/${id}`))
