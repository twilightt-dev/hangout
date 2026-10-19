<template>
  <section class="recommend-dishes" aria-labelledby="recommend-dishes-title">
    <header class="dish-heading">
      <div><h2 id="recommend-dishes-title">推荐菜</h2><p>吃过的好味道，推荐给下一位食客</p></div>
      <button type="button" data-test="dish-publish-open" @click="openForm">推荐一道菜</button>
    </header>
    <p v-if="error" role="alert" data-test="dish-error">{{ error }} <button type="button" data-test="dish-retry" :disabled="loading" @click="load">重试</button></p>
    <p v-if="loading && !page" aria-live="polite">推荐菜加载中…</p>
    <p v-if="!loading && !error && page && !page.records.length" class="dish-empty">暂无推荐菜，来分享你吃过的好味道吧。</p>
    <div v-if="page?.records.length" class="dish-grid" :aria-busy="loading">
      <article v-for="dish in page.records" :key="dish.id" class="dish-card">
        <img v-if="!brokenImages.has(dish.id)" :src="dish.coverImage" :alt="dish.name" loading="lazy" @error="brokenImages.add(dish.id)">
        <div v-else class="dish-placeholder" role="img" :aria-label="`${dish.name}暂无图片`">暂无图片</div>
        <div class="dish-card-body"><h3>{{ dish.name }}</h3><p>{{ dish.recommendCount }} 人推荐</p>
          <button type="button" :data-test="`recommend-${dish.id}`" :disabled="stateAvailable === false || dish.recommended || pending.has(dish.id) || loading" :aria-pressed="dish.recommended" @click="recommend(dish)">{{ stateAvailable === false ? '推荐状态暂不可用' : dish.recommended ? '已推荐' : pending.has(dish.id) ? '提交中…' : '我也推荐' }}</button>
        </div>
      </article>
    </div>
    <div class="dish-browse">
      <button v-if="preview && !listOnly && page && page.total > page.records.length" type="button" data-test="dish-more" @click="router.push(`/shops/${props.shopId}/lists/recommendations`)">查看更多推荐菜</button>
      <button v-else-if="!preview && !expanded && page && page.total > 6" type="button" data-test="dish-more" :disabled="loading" @click="setExpanded(true)">查看更多推荐菜</button>
      <template v-if="expanded">
        <nav v-if="page && page.pages > 1" aria-label="推荐菜分页"><button type="button" data-test="dish-prev" :disabled="loading || current <= 1" @click="changePage(current - 1)">上一页</button><span>{{ current }} / {{ page.pages }}</span><button type="button" data-test="dish-next" :disabled="loading || current >= page.pages" @click="changePage(current + 1)">下一页</button></nav>
        <button type="button" data-test="dish-collapse" :disabled="loading" @click="setExpanded(false)">收起</button>
      </template>
    </div>
    <p v-if="notice" role="status" class="dish-notice">{{ notice }}</p>
    <form v-if="formOpen && auth.isAuthenticated" class="dish-form" @submit.prevent="publish">
      <h3>新增推荐菜</h3><p>已有的菜品直接点“我也推荐”。新菜品发布后会计入你的一次推荐。</p>
      <label>菜品名称<input v-model="name" data-test="dish-name" maxlength="100" required :disabled="submitting" placeholder="例如：招牌烤鱼"></label>
      <label>上传一张菜品图片<input data-test="dish-file" type="file" accept="image/jpeg,image/png,image/gif" :disabled="uploading || submitting" @change="selectImage"></label>
      <small>支持 JPG、PNG、GIF，单张不超过 5 MB。</small>
      <img v-if="coverImage" class="dish-cover-preview" :src="coverImage" alt="待发布的菜品封面">
      <p v-if="uploadMessage" role="status">{{ uploadMessage }}</p>
      <p v-if="submitError" role="alert">{{ submitError }}</p>
      <div class="dish-form-actions"><button type="submit" data-test="dish-submit" :disabled="submitting || uploading || !name.trim() || !coverImage">{{ submitting ? '发布中…' : '发布推荐菜' }}</button><button type="button" :disabled="submitting || uploading" @click="formOpen = false">收起表单</button></div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getRecommendDishes, publishRecommendDish, recommendDish, type RecommendDish, type RecommendDishPage } from '@/api/recommendDish'
import { uploadBlogImage, normalizeBlogImageUrl } from '@/api/upload'

import { available, type Section } from '@/api/shopDetail'
const props = withDefaults(defineProps<{ shopId: number; preview?: Section<RecommendDishPage>; stateAvailable?: boolean; listOnly?: boolean }>(), { stateAvailable: true })
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const router = useRouter()
const page = ref<RecommendDishPage>()
const loading = ref(false)
const error = ref('')
const notice = ref('')
const current = ref(1)
const expanded = ref(false)
const formOpen = ref(false)
const name = ref('')
const coverImage = ref('')
const uploadMessage = ref('')
const submitError = ref('')
const uploading = ref(false)
const submitting = ref(false)
const pending = ref(new Set<number>())
const brokenImages = ref(new Set<number>())
let scope = 0
let loadVersion = 0
let controller: AbortController | undefined
let submission: { fingerprint: string; requestId: string } | undefined

function requireLogin() {
  if (auth.isAuthenticated) return true
  void router.push({ name: 'login', query: { redirect: `/shops/${props.shopId}` } })
  return false
}

async function load() {
  const version = ++loadVersion
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await getRecommendDishes(props.shopId, current.value, expanded.value ? 12 : 6, controller.signal)
    if (version !== loadVersion) return false
    page.value = result
    return true
  } catch {
    if (version === loadVersion) error.value = '推荐菜加载失败，请稍后重试'
    return false
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function setExpanded(value: boolean) {
  expanded.value = value; current.value = 1; page.value = undefined
  await load()
}
async function changePage(value: number) {
  // 导航到新页时清除旧内容，失败后重试目标页，不把旧记录标成新页。
  current.value = value; page.value = undefined
  await load()
}
function openForm() { if (requireLogin()) formOpen.value = true }

async function recommend(dish: RecommendDish) {
  if (!requireLogin() || dish.recommended || pending.value.has(dish.id)) return
  const executionScope = scope
  pending.value.add(dish.id)
  notice.value = ''
  try {
    const result = await recommendDish(props.shopId, dish.id)
    if (executionScope !== scope) return
    if (page.value) page.value.records = page.value.records.map(item => item.id === result.id ? result : item)
    notice.value = '推荐成功'
    emit('changed')
    if (!props.preview && !await load() && executionScope === scope) notice.value = '推荐已成功，列表刷新失败，请重试加载'
  } catch (failure) {
    if (executionScope === scope) notice.value = failure instanceof Error ? failure.message : '推荐未确认，请重试；重复提交不会增加人数'
  } finally {
    if (executionScope === scope) pending.value.delete(dish.id)
  }
}

async function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || uploading.value || submitting.value) return
  if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    uploadMessage.value = '请选择不超过 5 MB 的 JPG、PNG 或 GIF 图片'
    input.value = ''
    return
  }
  const executionScope = scope
  uploading.value = true
  uploadMessage.value = '图片上传中…'
  try {
    const path = await uploadBlogImage(file)
    if (executionScope !== scope) return
    coverImage.value = normalizeBlogImageUrl(path)
    uploadMessage.value = '图片上传成功'
  } catch {
    if (executionScope === scope) uploadMessage.value = '图片上传失败，请重试'
  } finally {
    if (executionScope === scope) { uploading.value = false; input.value = '' }
  }
}

async function publish() {
  if (!requireLogin() || submitting.value || uploading.value || !name.value.trim() || !coverImage.value) return
  const executionScope = scope
  const payload = { name: name.value.trim(), coverImage: coverImage.value }
  const fingerprint = JSON.stringify(payload)
  // 内容未改变时沿用提交标识，支持网络结果不明后的安全重试。
  if (submission?.fingerprint !== fingerprint) submission = { fingerprint, requestId: crypto.randomUUID() }
  submitting.value = true
  submitError.value = ''
  try {
    await publishRecommendDish(props.shopId, { ...payload, requestId: submission.requestId })
  } catch (failure) {
    if (executionScope === scope) {
      submitError.value = failure instanceof Error ? failure.message : '发布结果未确认，请保留表单重试'
      submitting.value = false
    }
    return
  }
  if (executionScope !== scope) return
  name.value = ''; coverImage.value = ''; uploadMessage.value = ''; submission = undefined
  formOpen.value = false; current.value = 1
  notice.value = '菜品已发布，已计入你的推荐'
  emit('changed')
  if (!props.preview && !await load() && executionScope === scope) notice.value = '菜品已发布，列表刷新失败，请重试加载'
  if (executionScope === scope) submitting.value = false
}

watch([() => props.shopId, () => auth.isAuthenticated, () => auth.user?.id], () => {
  scope += 1
  page.value = undefined; current.value = 1; expanded.value = false; formOpen.value = false
  name.value = ''; coverImage.value = ''; notice.value = ''; uploadMessage.value = ''; submitError.value = ''
  uploading.value = false; submitting.value = false; submission = undefined
  pending.value.clear(); brokenImages.value.clear()
  if (props.preview) {
    loadVersion += 1; controller?.abort(); loading.value = false
    page.value = props.preview.data ? { ...props.preview.data, records: [...props.preview.data.records] } : undefined
    error.value = available(props.preview) ? '' : props.preview.message || '推荐菜加载失败，请稍后重试'
  } else void load()
}, { immediate: true })
watch(() => props.preview, value => {
  if (!value) return
  loadVersion += 1; controller?.abort(); loading.value = false
  page.value = value.data ? { ...value.data, records: [...value.data.records] } : undefined
  error.value = available(value) ? '' : value.message || '推荐菜加载失败，请稍后重试'
})
onBeforeUnmount(() => { scope += 1; loadVersion += 1; controller?.abort() })
</script>

<style scoped>
.recommend-dishes{margin-top:28px;padding:20px;border:1px solid var(--color-border);border-radius:var(--radius-card);background:var(--color-surface)}
.dish-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:18px}.dish-heading h2{margin:0;font-size:1.3rem}.dish-heading p,.dish-form>p,.dish-form small{margin:6px 0;color:var(--color-muted);font-size:.85rem}
.recommend-dishes button{min-height:40px;padding:8px 12px;border:1px solid var(--color-border);border-radius:var(--radius-control);background:var(--color-surface);color:var(--color-primary);font:inherit;cursor:pointer}.recommend-dishes button:disabled{opacity:.55;cursor:default}.recommend-dishes button:focus-visible,.dish-form input:focus-visible{outline:2px solid var(--color-primary);outline-offset:3px}
.dish-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.dish-card{overflow:hidden;border:1px solid var(--color-border);border-radius:var(--radius-control)}.dish-card>img,.dish-placeholder{display:block;width:100%;aspect-ratio:4/3;object-fit:cover;background:#fff5ee}.dish-placeholder{display:grid;place-items:center;color:var(--color-muted)}.dish-card-body{padding:12px}.dish-card h3{margin:0;font-size:1rem;overflow-wrap:anywhere}.dish-card p{margin:7px 0;color:var(--color-muted);font-size:.85rem}.dish-card button{width:100%}
.dish-browse,.dish-browse nav{display:flex;justify-content:center;align-items:center;gap:12px;margin-top:16px;flex-wrap:wrap}.dish-browse nav{margin:0}.dish-empty{padding:20px 0;color:var(--color-muted)}.dish-notice{color:var(--color-primary)}
.dish-form{display:grid;gap:12px;margin-top:20px;padding-top:16px;border-top:1px solid var(--color-border)}.dish-form h3{margin:0}.dish-form label{display:grid;gap:6px}.dish-form input{min-width:0;max-width:100%;padding:10px;border:1px solid var(--color-border);border-radius:var(--radius-control);font:inherit}.dish-cover-preview{width:160px;height:120px;object-fit:cover;border-radius:var(--radius-control)}.dish-form-actions{display:flex;gap:12px}.recommend-dishes [role=alert]{color:#a52d25}
@media(min-width:768px){.dish-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:420px){.recommend-dishes{padding:14px}.dish-heading{align-items:flex-start}.dish-heading button{flex-shrink:0;font-size:.85rem}}
</style>
