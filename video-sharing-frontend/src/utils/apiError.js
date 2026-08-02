const KNOWN_MESSAGES = new Map([
  ['invalid username or password', '用户名或密码不正确。'],
  ['username already taken!', '该用户名已被使用。'],
  ['email already in use!', '该邮箱已被注册。'],
  ['username is required', '请输入用户名。'],
  ['password is required', '请输入密码。'],
  ['email address is invalid', '邮箱格式不正确。'],
  ['username must contain 3 to 50 characters', '用户名长度需为 3 到 50 个字符。'],
  ['username must not exceed 50 characters', '用户名不能超过 50 个字符。'],
  ['password must contain 8 to 72 characters', '密码长度需为 8 到 72 个字符。'],
  ['email must not exceed 100 characters', '邮箱不能超过 100 个字符。'],
  ['thumbnail must be an image file.', '封面必须是图片文件。'],
  ['comment service is temporarily unavailable', '评论服务暂时不可用，请稍后重试。'],
  ['video search is temporarily unavailable', '视频搜索暂时不可用，请稍后重试。'],
  ['requested content was not found', '请求的内容不存在。'],
  ['service is temporarily unavailable', '服务暂时不可用，请稍后重试。'],
  ['service failed unexpectedly', '服务暂时不可用，请稍后重试。'],
  ['comment not found', '评论不存在或已不可操作。'],
  ['comment command not found', '评论处理记录不存在。'],
  ['access denied', '没有权限执行此操作。'],
])

const STATUS_MESSAGES = new Map([
  [400, '提交的信息有误，请检查后重试。'],
  [401, '登录状态已失效，请重新登录。'],
  [403, '没有权限执行此操作。'],
  [404, '请求的内容不存在。'],
  [413, '上传文件过大，请选择较小的文件。'],
  [415, '文件格式不受支持，请更换文件后重试。'],
])

function responseMessage(error) {
  const data = error?.response?.data
  if (typeof data === 'string') return data.trim()
  if (typeof data?.message === 'string') return data.message.trim()
  return ''
}

export function friendlyApiError(error, fallback) {
  const knownMessage = KNOWN_MESSAGES.get(responseMessage(error).toLowerCase())
  if (knownMessage) return knownMessage

  const status = error?.response?.status
  if (STATUS_MESSAGES.has(status)) return STATUS_MESSAGES.get(status)
  if (Number.isInteger(status) && status >= 500) return '服务暂时不可用，请稍后重试。'
  return fallback
}
