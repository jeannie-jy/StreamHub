import { request } from './client'
import type {
  Activity,
  ActivityOrder,
  AuthSession,
  ChatMessage,
  GiftCatalog,
  GiftOrder,
  LiveRoom,
  ModerationLog,
  OpsSummary,
  PageResult,
  RankEntry,
  RoomDashboard,
  SensitiveWord,
  AuditLog,
  UserProfile,
  Wallet,
  WsTicket,
} from '@/types/api'

export const authApi = {
  register: (body: { username: string; nickname: string; password: string }) =>
    request<AuthSession>({ method: 'POST', url: '/v1/auth/register', data: body }),
  login: (body: { username: string; password: string }) =>
    request<AuthSession>({ method: 'POST', url: '/v1/auth/login', data: body }),
  refresh: () => request<AuthSession>({ method: 'POST', url: '/v1/auth/refresh' }),
  logout: () => request<void>({ method: 'POST', url: '/v1/auth/logout' }),
  me: () => request<AuthSession>({ method: 'GET', url: '/v1/auth/me' }),
  wsTicket: () => request<WsTicket>({ method: 'POST', url: '/v1/auth/ws-ticket' }),
}

export const userApi = {
  profile: (userId: number) => request<UserProfile>({ method: 'GET', url: `/v1/users/${userId}` }),
  updateMe: (body: { nickname: string; avatarUrl?: string | null }) =>
    request<UserProfile>({ method: 'PUT', url: '/v1/users/me', data: body }),
  follow: (anchorId: number) => request<{ following: boolean }>({ method: 'POST', url: `/v1/users/${anchorId}/follow` }),
  unfollow: (anchorId: number) => request<{ following: boolean }>({ method: 'DELETE', url: `/v1/users/${anchorId}/follow` }),
  followStatus: (anchorId: number) => request<{ following: boolean }>({ method: 'GET', url: `/v1/users/${anchorId}/follow-status` }),
}

export const liveApi = {
  rooms: (params: { page?: number; pageSize?: number; status?: string; category?: string; keyword?: string } = {}) =>
    request<PageResult<LiveRoom>>({ method: 'GET', url: '/v1/live/rooms', params }),
  mine: (params: { page?: number; pageSize?: number } = {}) =>
    request<PageResult<LiveRoom>>({ method: 'GET', url: '/v1/live/rooms/mine', params }),
  room: (roomId: number) => request<LiveRoom>({ method: 'GET', url: `/v1/live/rooms/${roomId}` }),
  createRoom: (body: { title: string; category: string; coverUrl?: string | null }) =>
    request<LiveRoom>({ method: 'POST', url: '/v1/live/rooms', data: body }),
  updateRoom: (roomId: number, body: { title: string; category: string; coverUrl?: string | null }) =>
    request<LiveRoom>({ method: 'PUT', url: `/v1/live/rooms/${roomId}`, data: body }),
  startRoom: (roomId: number) => request<LiveRoom>({ method: 'POST', url: `/v1/live/rooms/${roomId}/start` }),
  endRoom: (roomId: number) => request<LiveRoom>({ method: 'POST', url: `/v1/live/rooms/${roomId}/end` }),
  messages: (roomId: number, afterId = 0, limit = 50) =>
    request<ChatMessage[]>({ method: 'GET', url: `/v1/live/rooms/${roomId}/messages`, params: { afterId, limit } }),
  activities: (roomId: number) => request<Activity[]>({ method: 'GET', url: `/v1/live/rooms/${roomId}/activities` }),
  dashboard: (roomId: number) => request<RoomDashboard>({ method: 'GET', url: `/v1/live/rooms/${roomId}/dashboard` }),
}

export const giftApi = {
  catalog: () => request<GiftCatalog[]>({ method: 'GET', url: '/v1/gifts' }),
  wallet: () => request<Wallet>({ method: 'GET', url: '/v1/wallet' }),
  recharge: (body: { bizNo: string; amount: number }) => request<Wallet>({ method: 'POST', url: '/v1/wallet/recharge', data: body }),
  send: (roomId: number, body: { giftCode: string; quantity: number; clientOrderNo: string }) =>
    request<GiftOrder>({ method: 'POST', url: `/v1/live/rooms/${roomId}/gifts`, data: body }),
  order: (orderNo: string) => request<GiftOrder>({ method: 'GET', url: `/v1/gift-orders/${orderNo}` }),
  myOrders: (params: { page?: number; pageSize?: number } = {}) => request<PageResult<GiftOrder>>({ method: 'GET', url: '/v1/gift-orders/mine', params }),
  contributorRank: (roomId: number) => request<RankEntry[]>({ method: 'GET', url: `/v1/live/rooms/${roomId}/gift-rank` }),
  incomeRank: (roomId: number) => request<RankEntry[]>({ method: 'GET', url: `/v1/live/rooms/${roomId}/gift-income-rank` }),
}

export const activityApi = {
  get: (activityId: number) => request<Activity>({ method: 'GET', url: `/v1/activities/${activityId}` }),
  create: (roomId: number, body: { name: string; stock: number; unitPrice: number; startsAt: string; endsAt: string }) =>
    request<Activity>({ method: 'POST', url: `/v1/live/rooms/${roomId}/activities`, data: body }),
  start: (activityId: number) => request<Activity>({ method: 'POST', url: `/v1/activities/${activityId}/start` }),
  seckill: (activityId: number, clientOrderNo: string) =>
    request<ActivityOrder>({ method: 'POST', url: `/v1/activities/${activityId}/seckill`, data: { clientOrderNo } }),
  order: (orderNo: string) => request<ActivityOrder>({ method: 'GET', url: `/v1/activity-orders/${orderNo}` }),
  myOrders: (params: { page?: number; pageSize?: number } = {}) => request<PageResult<ActivityOrder>>({ method: 'GET', url: '/v1/activity-orders/mine', params }),
}

export const opsApi = {
  summary: () => request<OpsSummary>({ method: 'GET', url: '/v1/ops/dashboard/summary' }),
  rooms: (params: Record<string, string | number | undefined> = {}) => request<PageResult<LiveRoom>>({ method: 'GET', url: '/v1/ops/rooms', params }),
  stopRoom: (roomId: number, reason: string) => request<LiveRoom>({ method: 'POST', url: `/v1/ops/rooms/${roomId}/stop`, data: { reason } }),
  users: (params: Record<string, string | number | undefined> = {}) => request<PageResult<UserProfile>>({ method: 'GET', url: '/v1/ops/users', params }),
  banUser: (userId: number, reason: string) => request<UserProfile>({ method: 'POST', url: `/v1/ops/users/${userId}/ban`, data: { reason } }),
  unbanUser: (userId: number) => request<UserProfile>({ method: 'DELETE', url: `/v1/ops/users/${userId}/ban` }),
  activities: (params: Record<string, string | number | undefined> = {}) => request<PageResult<Activity>>({ method: 'GET', url: '/v1/ops/activities', params }),
  stopActivity: (activityId: number, reason: string) => request<Activity>({ method: 'POST', url: `/v1/ops/activities/${activityId}/stop`, data: { reason } }),
  sensitiveWords: () => request<SensitiveWord[]>({ method: 'GET', url: '/v1/ops/sensitive-words' }),
  addSensitiveWord: (word: string) => request<SensitiveWord>({ method: 'POST', url: '/v1/ops/sensitive-words', data: { word } }),
  removeSensitiveWord: (id: number) => request<void>({ method: 'DELETE', url: `/v1/ops/sensitive-words/${id}` }),
  auditLogs: (params: { page?: number; pageSize?: number } = {}) => request<PageResult<AuditLog>>({ method: 'GET', url: '/v1/ops/audit-logs', params }),
  moderationLogs: (params: { page?: number; pageSize?: number } = {}) => request<PageResult<ModerationLog>>({ method: 'GET', url: '/v1/ops/moderation-logs', params }),
  muteUser: (roomId: number, body: { userId: number; reason: string; expiresMinutes?: number | null }) => request<void>({ method: 'POST', url: `/v1/ops/rooms/${roomId}/mutes`, data: body }),
  unmuteUser: (roomId: number, userId: number) => request<void>({ method: 'DELETE', url: `/v1/ops/rooms/${roomId}/mutes/${userId}` }),
}
