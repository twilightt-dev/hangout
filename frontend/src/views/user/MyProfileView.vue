<template>
  <section class="profile-view">
    <PageState v-if="state !== 'ready'" :state="state" :message="errorMessage" @retry="load" />
    <template v-else-if="user">
      <ProfileHeader :user="user" :info="info" :own-profile="true" :followed="false" :busy="loggingOut" @edit="router.push({ name: 'edit-profile' })" @logout="requestLogout" />
      <div class="profile-tabs" role="tablist" aria-label="我的内容">
        <button id="notes-tab" type="button" role="tab" aria-controls="notes-panel" :aria-selected="activeTab === 'notes'" @click="activeTab = 'notes'">我的笔记</button>
        <button id="feed-tab" type="button" role="tab" aria-controls="feed-panel" :aria-selected="activeTab === 'feed'" @click="showFeed">关注动态</button>
      </div>
      <section v-if="activeTab === 'notes'" id="notes-panel" role="tabpanel" aria-labelledby="notes-tab" class="profile-content">
        <PageState v-if="!blogs.length && blogError" state="error" :message="blogError" @retry="loadBlogs" />
        <PageState v-else-if="!blogs.length" state="empty" />
        <div v-else class="blog-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog" @open="openBlog" @like="openBlog" /></div>
      </section>
      <section v-else id="feed-panel" role="tabpanel" aria-labelledby="feed-tab" class="profile-content">
        <div class="feed-toolbar"><button type="button" :disabled="feedLoading" @click="loadFeed(true)">刷新动态</button></div>
        <div ref="feedContainer" class="feed-list" role="region" aria-label="关注动态列表" :aria-busy="feedLoading" tabindex="0" @scroll.passive="onFeedScroll">
          <PageState v-if="!feedBlogs.length && feedState !== 'ready'" :state="feedState" :message="feedError || (feedState === 'empty' ? '暂无关注动态' : '')" @retry="() => loadFeed(true)" />
          <div v-else class="blog-grid"><BlogCard v-for="blog in feedBlogs" :key="blog.id" :blog="blog" @open="openBlog" @like="openBlog" /></div>
          <PageState v-if="feedError && feedBlogs.length" state="error" :message="feedError" @retry="() => loadFeed()" />
          <p v-else-if="feedLoading" class="feed-status" role="status">正在加载关注动态…</p>
          <p v-else-if="feedExhausted" class="feed-status" role="status">已经到底啦~</p>
          <button v-else-if="feedBlogs.length" type="button" class="feed-more" @click="loadFeed()">加载更多动态</button>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/api/user'
import { getFollowFeed, getMyBlogs } from '@/api/blog'
import { useAuthStore } from '@/stores/auth'
import ProfileHeader from '@/components/profile/ProfileHeader.vue'
import BlogCard from '@/components/content/BlogCard.vue'
import PageState from '@/components/common/PageState.vue'
import type { Blog, User, UserInfo } from '@/types/api'
type ViewState = 'loading' | 'error' | 'empty' | 'ready'
const router = useRouter(); const auth = useAuthStore(); const user = ref<User>(); const info = ref<UserInfo>({}); const blogs = ref<Blog[]>([]); const state = ref<ViewState>('loading'); const errorMessage = ref(''); const blogError = ref(''); const activeTab = ref<'notes' | 'feed'>('notes'); const feedBlogs = ref<Blog[]>([]); const feedState = ref<ViewState>('empty'); const feedError = ref(''); const feedLoading = ref(false); const feedExhausted = ref(false); const feedLastId = ref(Date.now()); const feedOffset = ref(0); const loggingOut = ref(false)
const feedContainer = ref<HTMLElement>()
let disposed = false
let resizeObserver: ResizeObserver | undefined
watch(feedContainer, (element) => {
  resizeObserver?.disconnect()
  if (element && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => { void fillFeedViewport() })
    resizeObserver.observe(element)
  }
})
onBeforeUnmount(() => { disposed = true; resizeObserver?.disconnect() })
async function loadBlogs() { blogError.value = ''; try { blogs.value = await getMyBlogs(1) } catch { blogError.value = '我的笔记加载失败，请稍后重试。' } }
async function load() { state.value = 'loading'; errorMessage.value = ''; try { const current = await auth.fetchCurrentUser(); if (current.id === undefined) throw new Error('缺少用户标识'); user.value = current; const [detail] = await Promise.all([getUserInfo(current.id), loadBlogs()]); info.value = detail || {}; state.value = 'ready' } catch { state.value = 'error'; errorMessage.value = '个人主页加载失败，请稍后重试。' } }
async function loadFeed(reset = false) {
  if (disposed || feedLoading.value || (!reset && feedExhausted.value)) return
  if (reset) {
    feedBlogs.value = []; feedLastId.value = Date.now(); feedOffset.value = 0
    feedExhausted.value = false; feedState.value = 'loading'
    if (feedContainer.value) feedContainer.value.scrollTop = 0
  }
  feedLoading.value = true; feedError.value = ''
  try {
    const result = await getFollowFeed(feedLastId.value, feedOffset.value)
    if (!result || !Array.isArray(result.list) || !Number.isFinite(result.minTime) || !Number.isInteger(result.offset) || result.offset! < 0) throw new Error('无效分页响应')
    const next = result.list
    if (next.length && (result.minTime! > feedLastId.value || (result.minTime === feedLastId.value && result.offset! <= feedOffset.value))) throw new Error('分页游标未前进')
    const known = new Set(feedBlogs.value.map((item) => item.id))
    for (const blog of next) {
      if (!known.has(blog.id)) { feedBlogs.value.push(blog); known.add(blog.id) }
    }
    feedLastId.value = result.minTime!; feedOffset.value = result.offset!
    feedExhausted.value = !next.length
    feedState.value = feedBlogs.value.length ? 'ready' : 'empty'
  } catch {
    feedError.value = '关注动态加载失败，请稍后重试。'
    feedState.value = feedBlogs.value.length ? 'ready' : 'error'
  } finally { feedLoading.value = false }
  await fillFeedViewport()
}
async function fillFeedViewport() {
  await nextTick()
  const element = feedContainer.value
  if (!disposed && activeTab.value === 'feed' && element && element.clientHeight > 0 && element.scrollHeight <= element.clientHeight && !feedLoading.value && !feedError.value && !feedExhausted.value) void loadFeed()
}
function showFeed() {
  activeTab.value = 'feed'
  if (feedState.value === 'empty' && !feedBlogs.value.length && !feedExhausted.value) void loadFeed(true)
  else void fillFeedViewport()
}
function onFeedScroll(event: Event) {
  const element = event.currentTarget as HTMLElement
  if (!feedError.value && element.scrollTop + element.clientHeight >= element.scrollHeight - 24) void loadFeed()
}
function openBlog(blog: Blog) { if (blog.id !== undefined) void router.push({ name: 'blog-detail', params: { id: blog.id } }) }
async function requestLogout() { if (!window.confirm('确认退出登录吗？')) return; loggingOut.value = true; try { await auth.signOut() } catch { /* auth store 的 finally 已负责清理；仍回到首页 */ } finally { await router.push({ name: 'home' }); loggingOut.value = false } }
void load()
</script>

<style scoped>
.profile-view{max-width:900px;margin:0 auto}.profile-tabs{display:flex;gap:8px;margin-top:20px;border-bottom:1px solid var(--color-border)}.profile-tabs button{min-height:44px;padding:8px 14px;border:0;border-bottom:3px solid transparent;background:transparent;color:var(--color-muted);font:inherit;font-weight:700;cursor:pointer}.profile-tabs button[aria-selected="true"]{border-bottom-color:var(--color-primary);color:var(--color-primary)}.profile-content{margin-top:16px}.blog-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.feed-list{max-height:62vh;overflow:auto}.feed-status{color:var(--color-muted);font-size:.85rem;text-align:center}@media(min-width:768px){.blog-grid{grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}}
.feed-toolbar{display:flex;justify-content:flex-end;margin-bottom:12px}.feed-toolbar button,.feed-more{min-height:44px;padding:8px 16px;border:1px solid var(--color-border);border-radius:var(--radius-control);background:var(--color-surface);color:var(--color-primary);font:inherit;font-weight:600;cursor:pointer}.feed-toolbar button:disabled{opacity:.6;cursor:wait}.feed-more{display:block;margin:16px auto}.feed-list:focus-visible{outline-offset:4px}
</style>
