<template>
  <div class="page">
    <div class="toolbar">
      <span class="label">公众号</span>
      <el-select v-model="accountId" placeholder="选择公众号" style="width: 220px" @change="loadDraft">
        <el-option v-for="a in accounts" :key="a.id" :label="`${a.name}（${a.appId}）`" :value="a.id" />
      </el-select>
      <el-radio-group v-model="mode">
        <el-radio-button value="visual">可视化</el-radio-button>
        <el-radio-button value="json">JSON</el-radio-button>
      </el-radio-group>
      <div class="spacer" />
      <el-tag v-if="draftStatus === 1" size="small" type="success" class="status-tag">已同步</el-tag>
      <el-tag v-else size="small" type="info" class="status-tag">草稿</el-tag>
      <span v-if="syncedTime" class="synced-at">同步于 {{ fmt(syncedTime) }}</span>
      <el-button v-permission="'mp:menu:save'" :disabled="!accountId" type="primary" @click="saveDraft">保存草稿</el-button>
      <el-button v-permission="'mp:menu:save'" :disabled="!accountId" type="success" @click="syncMenuNow">同步到微信</el-button>
      <el-button :disabled="!accountId" @click="pullRemote">拉取线上</el-button>
      <el-button v-permission="'mp:menu:save'" :disabled="!accountId" type="danger" plain @click="removeRemote">删除线上</el-button>
    </div>

    <div v-if="!accountId" class="empty">
      <el-empty description="请先选择公众号" />
    </div>

    <div v-else-if="mode === 'visual'" class="visual">
      <div v-for="(btn, index) in buttons" :key="index" class="level1">
        <div class="level1-head">
          <span class="level1-index">菜单 {{ index + 1 }}</span>
          <span class="level1-actions">
            <el-button size="small" link :disabled="index === 0" @click="move(index, -1)">上移</el-button>
            <el-button size="small" link :disabled="index === buttons.length - 1" @click="move(index, 1)">下移</el-button>
            <el-button size="small" link type="danger" @click="removeTop(index)">删除</el-button>
          </span>
        </div>
        <div class="level1-body">
          <div class="field">
            <span class="field-label">名称</span>
            <el-input v-model="btn.name" maxlength="30" />
          </div>
          <template v-if="!btn.sub_button.length">
            <div class="field">
              <span class="field-label">类型</span>
              <el-select v-model="btn.type" style="width: 100%">
                <el-option v-for="t in MENU_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </div>
            <div v-for="f in valueFields(btn)" :key="f.key" class="field">
              <span class="field-label">{{ f.label }}</span>
              <el-select v-if="f.key === 'key' || f.key === 'appid'" v-model="(btn as any)[f.key]"
                         filterable allow-create :placeholder="f.placeholder" style="width: 100%">
                <el-option v-for="opt in f.key === 'appid' ? appidOptions : keyOptions" :key="opt" :label="opt" :value="opt" />
              </el-select>
              <el-input v-else v-model="(btn as any)[f.key]" :placeholder="f.placeholder" />
            </div>
          </template>
          <div v-else class="field">
            <span class="field-label">（有子菜单时本项仅作父级，名称生效）</span>
          </div>

          <div v-if="btn.sub_button.length || showAddSub[index]" class="subs">
            <div v-for="(sub, si) in btn.sub_button" :key="si" class="sub">
              <div class="field">
                <span class="field-label">子菜单 {{ si + 1 }}</span>
                <div class="sub-actions">
                  <el-button size="small" link :disabled="si === 0" @click="moveSub(index, si, -1)">上移</el-button>
                  <el-button size="small" link :disabled="si === btn.sub_button.length - 1" @click="moveSub(index, si, 1)">下移</el-button>
                  <el-button size="small" link type="danger" @click="removeSub(index, si)">删除</el-button>
                </div>
              </div>
              <div class="field">
                <span class="field-label">名称</span>
                <el-input v-model="sub.name" maxlength="30" />
              </div>
              <div class="field">
                <span class="field-label">类型</span>
                <el-select v-model="sub.type" style="width: 100%">
                  <el-option v-for="t in MENU_TYPES" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </div>
              <div v-for="f in valueFields(sub)" :key="f.key" class="field">
                <span class="field-label">{{ f.label }}</span>
                <el-select v-if="f.key === 'key' || f.key === 'appid'" v-model="(sub as any)[f.key]"
                           filterable allow-create :placeholder="f.placeholder" style="width: 100%">
                  <el-option v-for="opt in f.key === 'appid' ? appidOptions : keyOptions" :key="opt" :label="opt" :value="opt" />
                </el-select>
                <el-input v-else v-model="(sub as any)[f.key]" :placeholder="f.placeholder" />
              </div>
            </div>
          </div>
          <el-button size="small" :disabled="btn.sub_button.length >= 5" @click="addSub(index)">+ 子菜单（{{ btn.sub_button.length }}/5）</el-button>
        </div>
      </div>
      <el-button type="primary" plain :disabled="buttons.length >= 3" @click="addTop">+ 一级菜单（{{ buttons.length }}/3）</el-button>
    </div>

    <div v-else class="json-mode">
      <el-input v-model="menuJson" type="textarea" :rows="22" spellcheck="false" class="json-input" />
      <div class="hint">
        与微信 menu/create 接口同构：{"button":[{ "name": "菜单名", "type": "click", "key": "KEY", "sub_button": [...] }]}。
        一级 ≤3 个、二级 ≤5 个；view 填 url，miniprogram 填 url/pagepath/appid，素材类填 media_id。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteRemoteMenu, getMenuDraft, getRemoteMenu, pageAccounts, pageRules, saveMenuDraft, syncMenu
} from '../api/mp'

interface MenuButton {
  name: string
  type?: string
  key?: string
  url?: string
  pagepath?: string
  appid?: string
  media_id?: string
  article_id?: string
  sub_button: MenuButton[]
}

const MENU_TYPES = [
  { value: 'click', label: '点击推事件 (click)' },
  { value: 'view', label: '跳转网页 (view)' },
  { value: 'miniprogram', label: '小程序 (miniprogram)' },
  { value: 'media_id', label: '下发素材 (media_id)' },
  { value: 'view_limited', label: '发布后图文 (view_limited)' },
  { value: 'article_id', label: '发布后文章 (article_id)' },
  { value: 'article_view_limited', label: '发布后文章跳转 (article_view_limited)' },
  { value: 'scancode_push', label: '扫码推事件 (scancode_push)' },
  { value: 'scancode_waitmsg', label: '扫码等待消息 (scancode_waitmsg)' },
  { value: 'pic_sysphoto', label: '系统拍照 (pic_sysphoto)' },
  { value: 'pic_photo_or_album', label: '拍照或相册 (pic_photo_or_album)' },
  { value: 'pic_weixin', label: '微信相册 (pic_weixin)' },
  { value: 'location_select', label: '发送位置 (location_select)' }
]

function valueFields(btn: MenuButton): { key: string; label: string; placeholder?: string }[] {
  switch (btn.type) {
    case 'click':
    case 'scancode_push':
    case 'scancode_waitmsg':
    case 'pic_sysphoto':
    case 'pic_photo_or_album':
    case 'pic_weixin':
    case 'location_select':
      return [{ key: 'key', label: 'Key', placeholder: '点击事件 Key（回复规则可匹配）' }]
    case 'view':
      return [{ key: 'url', label: '网页链接', placeholder: 'https://...' }]
    case 'miniprogram':
      return [
        { key: 'url', label: '网页链接（备用）' },
        { key: 'pagepath', label: '小程序页面路径' },
        { key: 'appid', label: '小程序 AppID' }
      ]
    case 'media_id':
    case 'view_limited':
      return [{ key: 'media_id', label: '素材 media_id' }]
    case 'article_id':
    case 'article_view_limited':
      return [{ key: 'article_id', label: '文章 article_id' }]
    default:
      return []
  }
}

const route = useRoute()
const accounts = ref<any[]>([])
const accountId = ref<number>()
const mode = ref<'visual' | 'json'>('visual')
const menuJson = ref('{"button":[]}')
const buttons = ref<MenuButton[]>([])
const draftStatus = ref<number>(0)
const syncedTime = ref<string | null>(null)
const showAddSub = reactive<Record<number, boolean>>({})

// 小程序 AppID 候选：账号表当前仅 subscription/service 两类（后端 MpAccount 校验"类型仅支持 subscription/service"），
// 无小程序类型，预留 miniprogram 过滤——命中为空时下拉无选项，仍可经 allow-create 手输外部小程序
const appidOptions = computed(() =>
  accounts.value.filter((a) => a.type === 'miniprogram').map((a) => a.appId as string))

// Key 候选 = 当前账号草稿按钮树（含子菜单，实时复用 loadDraft 已加载数据）+ 自动回复 click 规则 matchValue，去重
const ruleKeys = ref<string[]>([])
const keyOptions = computed(() => {
  const set = new Set<string>()
  const collect = (list: MenuButton[]) => {
    for (const b of list) {
      if (b.key) set.add(b.key)
      if (b.sub_button.length) collect(b.sub_button)
    }
  }
  collect(buttons.value)
  for (const k of ruleKeys.value) if (k) set.add(k)
  return [...set]
})

async function loadRuleKeys() {
  if (!accountId.value) {
    ruleKeys.value = []
    return
  }
  try {
    // 后端 /mp/rule/page 暂不支持 matchType 查询参数，拉回后前端过滤
    const data: any = await pageRules({ pageNum: 1, pageSize: 100, accountId: accountId.value })
    ruleKeys.value = (data.records || [])
      .filter((r: any) => r.matchType === 'click' && r.matchValue)
      .map((r: any) => r.matchValue as string)
  } catch {
    ruleKeys.value = [] // 加载失败选项为空，仍可手输
  }
}

function emptyButton(): MenuButton {
  return { name: '', type: 'click', key: '', sub_button: [] }
}

function jsonToButtons(json: string): MenuButton[] | null {
  try {
    const parsed = JSON.parse(json)
    const list = Array.isArray(parsed?.button) ? parsed.button : []
    return list.map((b: any) => ({
      name: b.name || '',
      type: b.type,
      key: b.key,
      url: b.url,
      pagepath: b.pagepath,
      appid: b.appid,
      media_id: b.media_id,
      article_id: b.article_id,
      sub_button: Array.isArray(b.sub_button)
        ? b.sub_button.map((s: any) => ({
            name: s.name || '', type: s.type, key: s.key, url: s.url,
            pagepath: s.pagepath, appid: s.appid, media_id: s.media_id, article_id: s.article_id,
            sub_button: []
          }))
        : []
    }))
  } catch {
    return null
  }
}

function buttonsToJson(): string {
  const clean = (b: MenuButton) => {
    const item: any = { name: b.name || undefined, type: b.type || undefined }
    for (const key of ['key', 'url', 'pagepath', 'appid', 'media_id', 'article_id']) {
      if ((b as any)[key]) item[key] = (b as any)[key]
    }
    return item
  }
  const out = buttons.value.map((b) => {
    const item = clean(b)
    if (b.sub_button.length) {
      item.sub_button = b.sub_button.map(clean)
      // 有子菜单时父级不需要类型与值
      for (const key of ['type', 'key', 'url', 'pagepath', 'appid', 'media_id', 'article_id']) delete item[key]
    }
    return item
  })
  return JSON.stringify({ button: out }, null, 2)
}

function syncFromMode() {
  if (mode.value === 'visual') {
    const parsed = jsonToButtons(menuJson.value)
    if (parsed) buttons.value = parsed
  } else {
    menuJson.value = buttonsToJson()
  }
}

watch(mode, syncFromMode)

async function loadDraft() {
  if (!accountId.value) return
  loadRuleKeys()
  const draft: any = await getMenuDraft(accountId.value)
  menuJson.value = draft.menuJson || '{"button":[]}'
  draftStatus.value = draft.status ?? 0
  syncedTime.value = draft.syncedTime
  const parsed = jsonToButtons(menuJson.value)
  buttons.value = parsed || []
  if (!parsed) ElMessage.warning('草稿 JSON 无法解析，请切换 JSON 模式修正')
}

async function saveDraftAction(): Promise<boolean> {
  if (!accountId.value) return false
  const json = mode.value === 'visual' ? buttonsToJson() : menuJson.value
  try {
    await saveMenuDraft(accountId.value, json)
    menuJson.value = json
    ElMessage.success('草稿已保存')
    return true
  } catch {
    return false
  }
}

async function saveDraft() {
  await saveDraftAction()
}

async function syncMenuNow() {
  if (!(await saveDraftAction())) return
  try {
    await syncMenu(accountId.value!)
    ElMessage.success('已同步到微信')
    await loadDraft()
  } catch { /* 错误已由拦截器提示 */ }
}

async function pullRemote() {
  try {
    const remote: any = await getRemoteMenu(accountId.value!)
    const json = JSON.stringify(remote, null, 2)
    menuJson.value = json
    buttons.value = jsonToButtons(json) || []
    draftStatus.value = 0
    ElMessage.success('已拉取线上菜单（尚未保存为草稿）')
  } catch { /* 错误已由拦截器提示 */ }
}

async function removeRemote() {
  await ElMessageBox.confirm('确认删除该公众号的线上菜单？本地草稿保留。', '删除线上菜单', { type: 'warning' })
  await deleteRemoteMenu(accountId.value!)
  ElMessage.success('线上菜单已删除')
  await loadDraft()
}

function addTop() {
  buttons.value.push(emptyButton())
  if (mode.value === 'json') menuJson.value = buttonsToJson()
}

function removeTop(index: number) {
  buttons.value.splice(index, 1)
  if (mode.value === 'json') menuJson.value = buttonsToJson()
}

function addSub(index: number) {
  buttons.value[index].sub_button.push(emptyButton())
  if (mode.value === 'json') menuJson.value = buttonsToJson()
}

function removeSub(index: number, subIndex: number) {
  buttons.value[index].sub_button.splice(subIndex, 1)
  if (mode.value === 'json') menuJson.value = buttonsToJson()
}

function move(index: number, step: number) {
  const target = index + step
  const [item] = buttons.value.splice(index, 1)
  buttons.value.splice(target, 0, item)
  if (mode.value === 'json') menuJson.value = buttonsToJson()
}

function moveSub(index: number, subIndex: number, step: number) {
  const subs = buttons.value[index].sub_button
  const target = subIndex + step
  const [item] = subs.splice(subIndex, 1)
  subs.splice(target, 0, item)
  if (mode.value === 'json') menuJson.value = buttonsToJson()
}

function fmt(time: string | null) {
  return (time || '').replace('T', ' ').slice(0, 19)
}

onMounted(async () => {
  const data: any = await pageAccounts({ pageNum: 1, pageSize: 100 })
  accounts.value = data.records
  const fromQuery = route.query.accountId ? Number(route.query.accountId) : undefined
  accountId.value = fromQuery ?? accounts.value[0]?.id
  if (accountId.value) await loadDraft()
})
</script>

<style scoped>
.page {
  padding: 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: auto;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.spacer {
  flex: 1;
}
.status-tag {
  margin-right: 4px;
}
.synced-at {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.visual {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.level1 {
  width: 300px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-bg-color);
}
.level1-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  border-radius: 6px 6px 0 0;
}
.level1-index {
  font-size: 13px;
  font-weight: 500;
}
.level1-body {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.field-label {
  width: 92px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.subs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-left: 2px solid var(--el-color-primary-light-9);
  padding-left: 8px;
}
.sub {
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sub-actions {
  display: flex;
  gap: 2px;
}
.json-mode {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.json-input :deep(textarea) {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
