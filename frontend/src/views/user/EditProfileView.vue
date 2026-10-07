<template>
  <section class="edit-profile">
    <header>
      <p>资料管理</p>
      <h1>个人资料</h1>
    </header>

    <PageState v-if="state !== 'ready'" :state="state" :message="errorMessage" @retry="load" />

    <section v-else class="profile-fields">
      <div class="avatar-editor">
        <div class="avatar-preview" data-test="avatar-preview">
          <img v-if="previewIcon" :src="previewIcon" :alt="`${user?.nickName || '用户'}的头像预览`" width="96" height="96">
          <span v-else aria-hidden="true">{{ (user?.nickName || '探').slice(0, 1) }}</span>
        </div>
        <p class="avatar-hint">选择一张图片或使用默认头像，保存后才会生效。</p>
        <label class="upload-button">
          上传头像
          <input type="file" accept="image/jpeg,image/png" @change="handleFileChange">
        </label>
      </div>

      <section class="default-avatar-section" aria-labelledby="default-avatar-title">
        <h2 id="default-avatar-title">默认头像</h2>
        <div class="default-avatar-grid">
          <button
            v-for="option in defaultAvatars"
            :key="option.id"
            :data-test="`default-avatar-${option.id}`"
            type="button"
            class="default-avatar"
            :class="{ selected: selected?.sourceType === 'DEFAULT' && selected.defaultId === option.id }"
            :aria-pressed="selected?.sourceType === 'DEFAULT' && selected.defaultId === option.id"
            @click="selectDefault(option)"
          >
            <img :src="option.icon" :alt="`默认头像 ${option.id}`" width="64" height="64">
          </button>
        </div>
      </section>

      <p v-if="avatarError" data-test="avatar-error" class="avatar-error" role="alert">{{ avatarError }}</p>
      <p v-if="avatarMessage" class="avatar-message" role="status">{{ avatarMessage }}</p>
      <div class="avatar-actions">
        <button type="button" class="primary-action" data-test="save-avatar" :disabled="busy || !selected" @click="saveSelection">保存头像</button>
        <button type="button" class="secondary-action" :disabled="busy || !selected" @click="cancelSelection">取消</button>
      </div>

      <dl>
        <div><dt>昵称</dt><dd>{{ user?.nickName || '未设置' }}</dd></div>
        <div><dt>简介</dt><dd>{{ info.introduce || '未设置' }}</dd></div>
        <div><dt>性别</dt><dd>{{ info.gender === undefined ? '未设置' : info.gender ? '男' : '女' }}</dd></div>
        <div><dt>城市</dt><dd>{{ info.city || '未设置' }}</dd></div>
        <div><dt>生日</dt><dd>{{ info.birthday || '未设置' }}</dd></div>
        <div><dt>积分</dt><dd>{{ info.credits ?? 0 }}</dd></div>
        <div><dt>等级</dt><dd>{{ info.level === undefined ? '未设置' : info.level ? '是' : '否' }}</dd></div>
      </dl>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getDefaultAvatars, getUserInfo, saveAvatar } from '@/api/user'
import { uploadAvatar } from '@/api/upload'
import { useAuthStore } from '@/stores/auth'
import PageState from '@/components/common/PageState.vue'
import type { AvatarOption, AvatarSelection, User, UserInfo } from '@/types/api'

type ViewState = 'loading' | 'error' | 'empty' | 'ready'
type PendingSelection = AvatarSelection & { preview: string }

const auth = useAuthStore()
const user = ref<User>()
const info = ref<UserInfo>({})
const defaultAvatars = ref<AvatarOption[]>([])
const selected = ref<PendingSelection>()
const state = ref<ViewState>('loading')
const errorMessage = ref('')
const avatarError = ref('')
const avatarMessage = ref('')
const busy = ref(false)
const previewIcon = computed(() => selected.value?.preview || user.value?.icon || '')

async function load() {
  state.value = 'loading'
  errorMessage.value = ''
  avatarError.value = ''
  try {
    const current = auth.user || await auth.fetchCurrentUser()
    if (current.id === undefined) throw new Error('缺少用户标识')
    const [detail, defaults] = await Promise.all([getUserInfo(current.id), getDefaultAvatars()])
    user.value = current
    info.value = detail || {}
    defaultAvatars.value = defaults || []
    state.value = 'ready'
  } catch {
    state.value = 'error'
    errorMessage.value = '资料加载失败，请稍后重试。'
  }
}

function selectDefault(option: AvatarOption) {
  avatarError.value = ''
  avatarMessage.value = ''
  selected.value = { sourceType: 'DEFAULT', defaultId: option.id, preview: option.icon }
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  avatarError.value = ''
  avatarMessage.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    avatarError.value = '仅支持 JPG、PNG 格式。'
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    avatarError.value = '头像图片不能超过 5 MB。'
    return
  }
  try {
    const uploadPath = await uploadAvatar(file)
    selected.value = { sourceType: 'UPLOAD', uploadPath, preview: uploadPath }
  } catch (error) {
    avatarError.value = error instanceof Error ? error.message : '头像上传失败，请稍后重试。'
  }
}

async function saveSelection() {
  if (!selected.value || busy.value) return
  busy.value = true
  avatarError.value = ''
  avatarMessage.value = ''
  const { preview: _preview, ...request } = selected.value
  try {
    const result = await saveAvatar(request)
    auth.updateAvatar(result.icon)
    user.value = { ...(user.value || {}), icon: result.icon }
    await auth.fetchCurrentUser()
    selected.value = undefined
    avatarMessage.value = '头像已保存。'
  } catch (error) {
    avatarError.value = error instanceof Error ? error.message : '头像保存失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

function cancelSelection() {
  selected.value = undefined
  avatarError.value = ''
  avatarMessage.value = ''
}

void load()
</script>

<style scoped>
.edit-profile{max-width:720px;margin:0 auto}.edit-profile header p{margin:0;color:var(--color-primary);font-size:.85rem;font-weight:700}.edit-profile h1{margin:4px 0 18px;font-size:clamp(1.7rem,4vw,2.4rem)}.profile-fields{padding:20px;border:1px solid var(--color-border);border-radius:var(--radius-card);background:var(--color-surface);box-shadow:var(--shadow-card)}.avatar-editor{text-align:center}.avatar-preview{display:grid;place-items:center;margin:0 auto 10px}.avatar-preview img,.avatar-preview span{width:96px;height:96px;border-radius:50%;object-fit:cover}.avatar-preview span{display:grid;place-items:center;background:#f7c8af;color:#884625;font-size:2rem;font-weight:800}.avatar-hint{margin:0 0 12px;color:var(--color-muted);font-size:.86rem}.upload-button,.avatar-actions button{display:inline-flex;min-height:44px;align-items:center;justify-content:center;padding:8px 14px;border:1px solid var(--color-primary);border-radius:var(--radius-control);font:inherit;font-weight:700;cursor:pointer}.upload-button{background:#fff0ea;color:var(--color-primary)}.upload-button input{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap}.default-avatar-section{margin-top:24px}.default-avatar-section h2{margin:0 0 12px;font-size:1rem}.default-avatar-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.default-avatar{display:grid;place-items:center;min-height:84px;padding:8px;border:2px solid transparent;border-radius:var(--radius-control);background:var(--color-surface);cursor:pointer}.default-avatar.selected{border-color:var(--color-primary);background:#fff0ea}.default-avatar img{width:64px;height:64px;border-radius:50%;object-fit:cover}.avatar-error,.avatar-message{margin:14px 0 0;font-size:.9rem}.avatar-error{color:#b42318}.avatar-message{color:#287a45}.avatar-actions{display:flex;gap:8px;margin:18px 0}.avatar-actions button{flex:1}.primary-action{background:var(--color-primary);color:white}.secondary-action{border-color:var(--color-border)!important;background:var(--color-surface);color:var(--color-muted)}.avatar-actions button:disabled{cursor:not-allowed;opacity:.55}.profile-fields dl{margin:0}.profile-fields dl div{display:grid;grid-template-columns:86px 1fr;gap:12px;padding:13px 0;border-top:1px solid var(--color-border)}.profile-fields dt{color:var(--color-muted)}.profile-fields dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}@media(max-width:420px){.default-avatar-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
