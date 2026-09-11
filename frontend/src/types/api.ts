export interface ApiResponse<T> {
  success: boolean
  data: T
  code: string
  message: string
  traceId: string
  timestamp: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  hasNext: boolean
}

export interface AuthSession {
  userId: number
  username?: string
  nickname?: string
  role?: string
  accessToken?: string
  expiresAt: string
}

export interface UserProfile {
  id: number
  username: string
  nickname: string
  avatarUrl?: string | null
  role: string
  status: string
  followerCount: number
}

export type RoomStatus = 'OFFLINE' | 'LIVE' | 'ENDED' | 'BLOCKED'

export interface LiveRoom {
  id: number
  anchorId: number
  anchorNickname?: string
  anchorAvatarUrl?: string | null
  title: string
  coverUrl?: string | null
  category?: string | null
  status: RoomStatus | string
  onlineCount: number
  pushUrl?: string | null
  playbackUrl?: string | null
  webrtcPlaybackUrl?: string | null
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  roomId: number
  userId: number
  clientMessageId: string
  content: string
  createdAt: string
}

export interface GiftCatalog {
  id: number
  code: string
  name: string
  price: number
  status: string
}

export interface Wallet {
  userId: number
  balance: number
}

export interface GiftOrder {
  orderNo: string
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  roomId: number
  senderId: number
  anchorId: number
  giftCode: string
  quantity: number
  totalAmount: number
  createdAt: string
  processedAt?: string | null
  failureReason?: string | null
}

export interface RankEntry {
  userId: number
  nickname?: string
  avatarUrl?: string | null
  amount: number
}

export interface Activity {
  id: number
  roomId: number
  name: string
  stock: number
  remainingStock: number
  unitPrice: number
  status: string
  startsAt: string
  endsAt: string
  createdAt: string
  updatedAt: string
}

export interface ActivityOrder {
  orderNo: string
  activityId: number
  userId: number
  status: string
  createdAt: string
  processedAt?: string | null
  closedAt?: string | null
}

export interface RoomDashboard {
  roomId: number
  onlineCount: number
  giftAmount: number
  giftOrderCount: number
  chatMessageCount: number
  activityOrderCount: number
}

export interface WsTicket {
  ticket: string
  expiresAt: string
}

export interface OpsSummary {
  liveRoomCount: number
  onlineCount: number
  giftOrderCount: number
  activityOrderCount: number
  pendingOrderCount: number
  bannedUserCount: number
}

export interface SensitiveWord {
  id: number
  word: string
  status: string
  createdAt: string
}

export interface AuditLog {
  id: number
  operatorId: number
  action: string
  targetType: string
  targetId: string
  reason?: string | null
  createdAt: string
}

export interface ModerationLog {
  id: number
  roomId: number
  userId: number
  content: string
  matchedWord: string
  action: string
  createdAt: string
}
