/** 后端统一响应结构。 */
export interface ApiResult<T> {
  code: number
  msg?: string | null
  data?: T | null
}

/** 后端签发的 Access Token 与可轮换的 Refresh Token。 */
export interface TokenVO {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessTtl: number
}

/** 后端 Long 在 JSON 中的表示。 */
export type Id = number

/** Spring LocalDate / LocalDateTime 序列化后的字符串。 */
export type DateValue = string

export interface User {
  id?: Id
  phone?: string
  password?: string
  nickName?: string
  icon?: string
  createTime?: DateValue
  updateTime?: DateValue
}

/** 用户主页公开信息，后端对应 UserVO。 */
export interface UserVO {
  id?: Id
  nickName?: string
  icon?: string
  createTime?: DateValue
  city?: string
  introduce?: string
  fans?: number
  followee?: number
}

export interface CommonFollowVO {
  id?: Id
  nickName?: string
  icon?: string
}

export interface UserInfo {
  userId?: Id
  city?: string
  introduce?: string
  fans?: number
  followee?: number
  gender?: boolean
  birthday?: DateValue
  credits?: number
  level?: boolean
  createTime?: DateValue
  updateTime?: DateValue
}

export interface SignResult {
  date: DateValue
  alreadySigned: boolean
}

export interface SignStats {
  date: DateValue
  month: string
  todaySigned: boolean
  monthlyDays: number
  continuousDays: number
  previousMonthDays: number
  monthDifference: number
}

export type AvatarSourceType = 'UPLOAD' | 'DEFAULT'

export interface AvatarOption {
  id: string
  icon: string
}

export interface AvatarSelection {
  sourceType: AvatarSourceType
  uploadPath?: string
  defaultId?: string
}

export interface AvatarVO {
  icon: string
  sourceType: AvatarSourceType
}

export interface ShopType {
  id?: Id
  name?: string
  icon?: string
  sort?: number
  createTime?: DateValue
  updateTime?: DateValue
}

export interface Shop {
  id?: Id
  name?: string
  typeId?: Id
  images?: string
  area?: string
  address?: string
  x?: number
  y?: number
  avgPrice?: number
  sold?: number
  comments?: number
  score?: number
  openHours?: string
  distance?: number
  createTime?: DateValue
  updateTime?: DateValue
}

export interface ShopReview {
  id: Id; shopId: Id; userId: Id; userName: string; userIcon?: string | null
  overallScore: number; tasteScore: number; environmentScore: number; serviceScore: number
  content: string; tags: string[]; createTime: DateValue
}
export interface ShopReviewPage { records: ShopReview[]; total: number; current: number; size: number; pages: number; averageScore?: number | null }
export interface ShopReviewTagOption { code: string; name: string }

export interface Voucher {
  id?: Id
  shopId?: Id
  title?: string
  subTitle?: string
  rules?: string
  payValue?: number
  actualValue?: number
  type?: number
  status?: number
  stock?: number
  beginTime?: DateValue
  endTime?: DateValue
  createTime?: DateValue
  updateTime?: DateValue
}

export interface ShopSetmealItem {
  name: string
  quantity: number
  unit: string
}

export interface ShopSetmeal {
  id: Id
  shopId: Id
  name: string
  coverImage: string
  price: number
  originalPrice?: number | null
  people: number
  description?: string | null
  rules: string
  items: ShopSetmealItem[]
}

export interface Blog {
  id?: Id
  shopId?: Id
  userId?: Id
  icon?: string
  name?: string
  isLike?: boolean
  title?: string
  images?: string
  content?: string
  liked?: number
  comments?: number
  createTime?: DateValue
  updateTime?: DateValue
}

export interface Comment {
  id?: Id
  blogId?: Id
  userId?: Id
  userName?: string
  userIcon?: string
  content?: string
  createTime?: DateValue
  updateTime?: DateValue
  liked?: number
  isLike?: boolean | null
  likeAvailable?: boolean
}

export interface PageResult<T> {
  records?: T[]
  current?: number
  size?: number
  total?: number
  pages?: number
}

export interface PaginationQuery {
  current?: number
}

export interface ShopTypeQuery extends PaginationQuery {
  typeId: Id
  sort?: 'distance' | 'comments' | 'score'
  longitude?: number
  latitude?: number
}

export interface ShopNameQuery extends PaginationQuery {
  name?: string
}

export interface BlogFeedQuery extends PaginationQuery {}

export interface ScrollPaginationQuery {
  lastId: number
  offset: number
}

export interface ScrollResult<T> {
  list?: T[]
  minTime?: number
  offset?: number
}
