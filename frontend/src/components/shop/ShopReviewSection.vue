<template>
  <section class="reviews" aria-labelledby="reviews-title">
    <div class="section-heading"><h2 id="reviews-title">店铺评价</h2><span v-if="page">{{ page.total }} 条 · {{ page.averageScore == null ? '暂无评分' : page.averageScore.toFixed(1) }} 分</span></div>
    <p v-if="error" class="review-error">{{ error }} <button @click="load">重试</button></p>
    <p v-else-if="loading">评价加载中…</p>
    <template v-else>
      <p v-if="!page?.records.length" class="empty">暂无评价，欢迎成为第一位评价者。</p>
      <article v-for="review in page?.records" :key="review.id" class="review-card">
        <img class="avatar" :src="review.userIcon || defaultAvatar" alt="">
        <div><strong>{{ review.userName }}</strong><span class="stars">★ {{ review.overallScore.toFixed(1) }}</span><p>{{ review.content }}</p><small>{{ review.createTime.replace('T',' ') }}</small><div class="tags"><span v-for="tag in review.tags" :key="tag">{{ tagName(tag) }}</span></div></div>
      </article>
      <nav v-if="page && page.pages > 1" class="pager"><button :disabled="current <= 1" @click="change(current - 1)">上一页</button><span>{{ current }} / {{ page.pages }}</span><button :disabled="current >= page.pages" @click="change(current + 1)">下一页</button></nav>
    </template>
    <p v-if="!auth.isAuthenticated" class="login-hint"><button type="button" @click="router.push({ name: 'login', query: { redirect: `/shops/${props.shopId}` } })">登录后发布评价</button></p>
    <form v-else class="review-form" @submit.prevent="submit">
      <h3>写评价</h3><div class="scores"><label v-for="field in scoreFields" :key="field.key">{{ field.name }}<select v-model.number="form[field.key]" :disabled="submitting"><option v-for="score in scores" :key="score" :value="score">{{ score.toFixed(1) }}</option></select></label></div>
      <textarea v-model="form.content" maxlength="1000" required placeholder="分享你的用餐体验" :disabled="submitting"></textarea>
      <div class="tag-picker"><button v-for="tag in tags" :key="tag.code" type="button" :disabled="submitting" :class="{selected: form.tags.includes(tag.code)}" @click="toggle(tag.code)">{{ tag.name }}</button></div>
      <button type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '发布评价' }}</button>
    </form>
  </section>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getReviewTags, getShopReviews, publishShopReview } from '@/api/shopReview'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import type { ShopReviewPage, ShopReviewTagOption } from '@/types/api'
const props = defineProps<{ shopId: number }>(); const emit = defineEmits<{ stats: [score: number | null, count: number] }>(); const auth = useAuthStore(); const router = useRouter()
const page = ref<ShopReviewPage>(); const tags = ref<ShopReviewTagOption[]>([]); const loading = ref(false); const error = ref(''); const current = ref(1); const submitting = ref(false); const defaultAvatar = '/imgs/icons/default-01.png'
const scores = [1,1.5,2,2.5,3,3.5,4,4.5,5]; const scoreFields = [{key:'overallScore',name:'总体'},{key:'tasteScore',name:'口味'},{key:'environmentScore',name:'环境'},{key:'serviceScore',name:'服务'}] as const
const form = reactive({overallScore: 5,tasteScore: 5,environmentScore: 5,serviceScore: 5,content:'',tags:[] as string[]}); let requestId = ''; let loadEpoch = 0
function tagName(code:string){return tags.value.find(tag=>tag.code===code)?.name || code}; function toggle(code:string){const i=form.tags.indexOf(code); if(i>=0) form.tags.splice(i,1); else if(form.tags.length<5) form.tags.push(code)}
async function load(){const epoch=++loadEpoch;loading.value=true;error.value='';try{const result=await getShopReviews(props.shopId,current.value);if(epoch!==loadEpoch)return false;page.value=result;emit('stats',result.averageScore ?? null,result.total);return true}catch{if(epoch===loadEpoch){error.value='评价加载失败，请稍后重试';emit('stats',null,-1)}return false}finally{if(epoch===loadEpoch)loading.value=false}}
async function change(next:number){current.value=next;await load()}
async function submit(){if(!requestId) requestId=crypto.randomUUID();submitting.value=true;try{await publishShopReview(props.shopId,{...form,requestId})}catch(error){ElMessage.error(error instanceof Error ? error.message : '评价未发布，请重试');submitting.value=false;return}ElMessage.success('评价已发布');Object.assign(form,{overallScore:5,tasteScore:5,environmentScore:5,serviceScore:5,content:'',tags:[]});requestId='';current.value=1;if(!await load()) ElMessage.warning('评价已发布，列表刷新失败');submitting.value=false}
onMounted(async()=>{try{tags.value=await getReviewTags()}catch{} await load()});watch(()=>props.shopId,()=>{requestId='';current.value=1;void load()});defineExpose({load,submit})
</script>
<style scoped>
.reviews{margin-top:28px;padding:20px;border:1px solid var(--color-border);border-radius:var(--radius-card);background:var(--color-surface)}.review-card{display:grid;grid-template-columns:40px 1fr;gap:12px;padding:14px 0;border-top:1px solid var(--color-border)}.avatar{width:40px;height:40px;border-radius:50%;object-fit:cover}.review-card p{margin:8px 0}.stars{margin-left:12px;color:#b56a09}.tags span,.tag-picker button{margin:4px;padding:3px 8px;border-radius:12px;border:1px solid var(--color-border);background:transparent}.tag-picker button.selected{background:#fde9dc}.scores{display:flex;gap:10px;flex-wrap:wrap}.scores label{display:grid;gap:4px;font-size:.85rem}.review-form{display:grid;gap:12px;margin-top:20px}.review-form textarea{min-height:90px;padding:8px}.review-form>button,.pager button{padding:7px 14px}.pager{display:flex;justify-content:center;gap:12px;margin-top:12px}.empty,.review-error{color:var(--color-muted)}
</style>
