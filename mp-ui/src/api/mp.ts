import request from './request'

// ==================== 类型 ====================
export interface MpAccount {
  id: number
  appId: string
  name: string
  type: string
  verified: number
  encryptMode: string
  status: number
  remark: string | null
  createTime: string
  updateTime: string
}

export interface MpFan {
  id: number
  accountId: number
  openId: string
  subscribed: number
  subscribeTime: string | null
  unsubscribeTime: string | null
  subscribeScene: string | null
  nickname: string | null
  headImgUrl: string | null
  sex: number
  province: string | null
  city: string | null
  unionId: string | null
  profileIncomplete: number
  lastActiveTime: string | null
}

export interface MpMessage {
  id: number
  accountId: number
  openId: string
  direction: string
  kind: string
  type: string
  msgId: string | null
  dedupKey: string
  payload: string | null
  rawXml: string | null
  replyContent: string | null
  traceId: string | null
  createTime: string
}

export interface MpRule {
  id?: number
  accountId: number
  name: string
  matchType: string
  matchValue: string | null
  replyType: string
  replyContent: string
  priority: number
  enabled: number
}

export interface TokenView {
  expiresAt: string | null
  valid: boolean | null
  errCode: string | null
  errMsg: string | null
}

export interface CredentialView {
  appId: string
  callbackToken: string
  aesKey: string
  encryptMode: string
}

export interface DraftView {
  menuJson: string
  status: number
  syncedTime: string | null
}

export interface TrendPoint {
  date: string
  eventCount: number
  messageCount: number
}

export interface Summary {
  accountTotal: number
  accountEnabled: number
  fanSubscribed: number
  messageToday: number
  ruleEnabled: number
}

// ==================== 公众号 ====================
export const pageAccounts = (params: { pageNum: number; pageSize: number; keyword?: string; status?: number }) =>
  request.get('/mp/account/page', { params })
export const getAccount = (id: number) => request.get(`/mp/account/${id}`)
export const createAccount = (data: object) => request.post('/mp/account', data)
export const updateAccount = (data: object) => request.put('/mp/account', data)
export const changeAccountStatus = (id: number, enable: boolean) =>
  request.post(`/mp/account/${id}/status`, null, { params: { enable } })
export const testAccount = (id: number) => request.post(`/mp/account/${id}/test`)
export const deleteAccount = (id: number) => request.delete(`/mp/account/${id}`)
export const getCredentials = (id: number) => request.get(`/mp/account/${id}/credentials`)
export const getTokenStatus = (id: number) => request.get(`/mp/account/${id}/token-status`)

// ==================== 菜单 ====================
export const getMenuDraft = (accountId: number) => request.get(`/mp/menu/${accountId}`)
export const saveMenuDraft = (accountId: number, menuJson: string) =>
  request.put(`/mp/menu/${accountId}`, { menuJson })
export const syncMenu = (accountId: number) => request.post(`/mp/menu/${accountId}/sync`)
export const getRemoteMenu = (accountId: number) => request.get(`/mp/menu/${accountId}/remote`)
export const deleteRemoteMenu = (accountId: number) => request.delete(`/mp/menu/${accountId}/remote`)

// ==================== 粉丝 ====================
export const pageFans = (params: {
  pageNum: number
  pageSize: number
  accountId?: number
  subscribed?: number
  keyword?: string
}) => request.get('/mp/fan/page', { params })

// ==================== 消息 ====================
export const pageMessages = (params: {
  pageNum: number
  pageSize: number
  accountId?: number
  direction?: string
  kind?: string
  type?: string
  keyword?: string
}) => request.get('/mp/message/page', { params })
export const getMessage = (id: number) => request.get(`/mp/message/${id}`)

// ==================== 自动回复规则 ====================
export const pageRules = (params: { pageNum: number; pageSize: number; accountId?: number; keyword?: string }) =>
  request.get('/mp/rule/page', { params })
export const createRule = (data: object) => request.post('/mp/rule', data)
export const updateRule = (data: object) => request.put('/mp/rule', data)
export const changeRuleStatus = (id: number, enable: boolean) =>
  request.post(`/mp/rule/${id}/status`, null, { params: { enable } })
export const deleteRule = (id: number) => request.delete(`/mp/rule/${id}`)

// ==================== 统计 ====================
export const getSummary = () => request.get('/mp/stats/summary')
export const getTrend = (params: { days?: number; accountId?: number }) =>
  request.get('/mp/stats/trend', { params })
