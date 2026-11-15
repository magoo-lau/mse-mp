<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="名称 / AppID" clearable style="width: 200px" @keyup.enter="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
        <el-option label="启用" :value="0" />
        <el-option label="停用" :value="1" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
      <el-button v-permission="'mp:account:save'" type="primary" plain @click="openCreate">接入公众号</el-button>
    </div>

    <el-table :data="rows" size="small" border>
      <el-table-column prop="name" label="名称" min-width="130" show-overflow-tooltip />
      <el-table-column prop="appId" label="AppID" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.type === 'service' ? 'success' : 'info'">
            {{ row.type === 'service' ? '服务号' : '订阅号' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="认证" width="70" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.verified ? 'success' : 'warning'">{{ row.verified ? '已认证' : '未认证' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="encryptMode" label="加密模式" width="90" align="center" />
      <el-table-column label="Token 状态" width="110" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="tokenViews[row.id]?.errCode" :content="tokenViews[row.id]?.errMsg" placement="top">
            <el-tag size="small" type="danger">失败 {{ tokenViews[row.id]?.errCode }}</el-tag>
          </el-tooltip>
          <el-tag v-else-if="tokenViews[row.id]?.valid" size="small" type="success">有效</el-tag>
          <el-tag v-else size="small" type="info">{{ tokenViews[row.id]?.errCode ? '' : '未获取' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70" align="center">
        <template #default="{ row }">
          <el-switch v-permission="'mp:account:save'" :model-value="row.status === 0" @change="changeStatus(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="150">
        <template #default="{ row }">{{ fmt(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="goMenu(row)">菜单</el-button>
          <el-button v-permission="'mp:account:save'" size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'mp:account:save'" size="small" link type="success" @click="test(row)">测试</el-button>
          <el-button size="small" link @click="openAccess(row)">接入配置</el-button>
          <el-button v-permission="'mp:account:delete'" size="small" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNum" v-model:size="query.pageSize" @change="load" />

    <!-- 新建/编辑 -->
    <el-dialog v-model="editDialog" :title="editForm.id ? '编辑公众号' : '接入公众号'" width="520px">
      <el-form label-width="110px" size="small">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="AppID" required>
          <el-input v-model="editForm.appId" :disabled="!!editForm.id" placeholder="wx 开头" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="editForm.type">
            <el-radio value="subscription">订阅号</el-radio>
            <el-radio value="service">服务号</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="已认证">
          <el-switch v-model="editForm.verified" />
        </el-form-item>
        <el-form-item label="AppSecret" required>
          <el-input v-model="editForm.appSecret" show-password :placeholder="editForm.id ? '留空 = 保持不变' : '公众平台-开发-基本配置'" />
        </el-form-item>
        <el-form-item label="Token" required>
          <el-input v-model="editForm.callbackToken" show-password :placeholder="editForm.id ? '留空 = 保持不变' : '服务器配置 Token（3-32 位）'" />
        </el-form-item>
        <el-form-item label="EncodingAESKey" required>
          <el-input v-model="editForm.aesKey" show-password :placeholder="editForm.id ? '留空 = 保持不变' : '43 位随机串'" />
        </el-form-item>
        <el-form-item label="消息加密方式">
          <el-select v-model="editForm.encryptMode" style="width: 100%">
            <el-option label="明文模式 plain" value="plain" />
            <el-option label="兼容模式 compatible" value="compatible" />
            <el-option label="安全模式 safe" value="safe" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <div class="hint">凭据 AES-GCM 加密落库，接口不回显；与公众平台「服务器配置」保持一致。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 接入配置 -->
    <el-dialog v-model="accessDialog" title="接入配置" width="640px">
      <el-form label-width="110px" size="small">
        <el-form-item label="回调 URL">
          <div class="copy-line">
            <el-input :model-value="accessForm.callbackUrl" readonly />
            <el-button size="small" @click="copy(accessForm.callbackUrl)">复制</el-button>
          </div>
        </el-form-item>
        <el-form-item label="Token">
          <div class="copy-line">
            <el-input :model-value="accessForm.callbackToken" readonly />
            <el-button size="small" @click="copy(accessForm.callbackToken)">复制</el-button>
          </div>
        </el-form-item>
        <el-form-item label="EncodingAESKey">
          <div class="copy-line">
            <el-input :model-value="accessForm.aesKey" readonly />
            <el-button size="small" @click="copy(accessForm.aesKey)">复制</el-button>
          </div>
        </el-form-item>
        <el-form-item label="消息加密方式">
          <span>{{ accessForm.encryptMode }}</span>
        </el-form-item>
      </el-form>
      <div class="hint">
        在公众平台「设置与开发-基本配置-服务器配置」中：URL 填上方回调地址，Token 与 EncodingAESKey
        与此处保持一致，消息加解密方式选择「{{ accessForm.encryptMode }}」，然后点击公众平台「启用」。
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  changeAccountStatus, createAccount, deleteAccount, getCredentials, getTokenStatus, pageAccounts,
  testAccount, updateAccount, type MpAccount, type TokenView
} from '../api/mp'
import { Pagination } from 'mse-ui-shared/pc'

const router = useRouter()
const auth = useAuthStore()
const rows = ref<MpAccount[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined as number | undefined })
const tokenViews = ref<Record<number, TokenView | undefined>>({})

const editDialog = ref(false)
const editForm = reactive<{ id?: number; name: string; appId: string; type: string; verified: boolean;
  appSecret: string; callbackToken: string; aesKey: string; encryptMode: string; remark: string }>(initForm())

const accessDialog = ref(false)
const accessForm = reactive({ callbackUrl: '', callbackToken: '', aesKey: '', encryptMode: '' })

function initForm() {
  return { name: '', appId: '', type: 'subscription', verified: false,
    appSecret: '', callbackToken: '', aesKey: '', encryptMode: 'safe', remark: '' }
}

function fmt(time: string) {
  return (time || '').replace('T', ' ').slice(0, 19)
}

async function load() {
  const data: any = await pageAccounts(query)
  rows.value = data.records
  total.value = Number(data.total)
  // token 状态逐号拉取（失败原因在 tooltip 中展示）
  for (const row of rows.value) {
    getTokenStatus(row.id).then((v: any) => (tokenViews.value[row.id] = v)).catch(() => {})
  }
}

function openCreate() {
  Object.assign(editForm, initForm())
  editForm.id = undefined
  editDialog.value = true
}

function openEdit(row: MpAccount) {
  Object.assign(editForm, initForm())
  editForm.id = row.id
  editForm.name = row.name
  editForm.appId = row.appId
  editForm.type = row.type
  editForm.verified = row.verified === 1
  editForm.encryptMode = row.encryptMode
  editForm.remark = row.remark || ''
  editDialog.value = true
}

async function save() {
  if (!editForm.name.trim() || !editForm.appId.trim()) {
    ElMessage.warning('请填写名称与 AppID')
    return
  }
  const body = {
    id: editForm.id,
    appId: editForm.appId,
    name: editForm.name,
    type: editForm.type,
    verified: editForm.verified,
    encryptMode: editForm.encryptMode,
    appSecret: editForm.appSecret,
    callbackToken: editForm.callbackToken,
    aesKey: editForm.aesKey,
    remark: editForm.remark
  }
  if (editForm.id) {
    await updateAccount(body)
  } else {
    await createAccount(body)
  }
  ElMessage.success('已保存')
  editDialog.value = false
  load()
}

async function changeStatus(row: MpAccount, enable: any) {
  await changeAccountStatus(row.id, !!enable)
  load()
}

async function test(row: MpAccount) {
  const result: any = await testAccount(row.id)
  if (result?.ok) {
    ElMessage.success(`「${row.name}」凭据有效，access_token 获取成功`)
  } else {
    ElMessage.error(result?.message || '测试失败')
  }
  load()
}

async function openAccess(row: MpAccount) {
  const cred: any = await getCredentials(row.id)
  // 回调 URL 带租户段（/mp/callback/{tenantId}/{appId}）：无登录态回调凭 URL 世界段定位租户库
  accessForm.callbackUrl = `${window.location.origin}/api/mp/callback/${auth.tenantId}/${row.appId}`
  accessForm.callbackToken = cred.callbackToken || ''
  accessForm.aesKey = cred.aesKey || ''
  accessForm.encryptMode = cred.encryptMode || 'safe'
  accessDialog.value = true
}

async function remove(row: MpAccount) {
  await ElMessageBox.confirm(
    `确认删除「${row.name}」？其粉丝、消息、菜单、回复规则将被一并删除。`, '删除', { type: 'warning' })
  await deleteAccount(row.id)
  ElMessage.success('已删除')
  load()
}

function goMenu(row: MpAccount) {
  router.push({ path: '/menu', query: { accountId: String(row.id) } })
}

// navigator.clipboard 仅在安全上下文（https/localhost）存在，经 IP 的 http 环境是 undefined，
// 统一退回隐藏 textarea + execCommand 的同步复制兜底（wujie 会把 execCommand 转发到主文档，嵌入模式同样生效）
function legacyCopy(text: string) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

async function copyText(text: string) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      legacyCopy(text)
    }
  } catch {
    legacyCopy(text)
  }
}

async function copy(text: string) {
  await copyText(text)
  ElMessage.success('已复制')
}

onMounted(load)
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
  margin-bottom: 10px;
  align-items: center;
}
.spacer {
  flex: 1;
}
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
.copy-line {
  display: flex;
  gap: 8px;
  width: 100%;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
