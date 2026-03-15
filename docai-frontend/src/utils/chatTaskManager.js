/**
 * 全局AI对话任务管理器（单例）
 * 管理后台AI请求，确保页面切换不中断AI处理
 */

let _currentTask = null

/**
 * 创建并启动一个后台AI任务
 * @param {Function} apiCall - 返回Promise的API调用函数，接收AbortController参数
 * @returns {object} task对象
 */
export function startTask(apiCall) {
  const abortController = new AbortController()
  const task = {
    status: 'pending',
    response: null,
    error: null,
    abortController,
    _listeners: new Set()
  }

  apiCall(abortController)
    .then(res => {
      const reply = res?.data?.reply || res?.reply || res?.data || res || ''
      task.status = 'completed'
      task.response = reply
      task._listeners.forEach(fn => fn('complete', reply))
      task._listeners.clear()
    })
    .catch(err => {
      if (err.name === 'AbortError' || abortController.signal.aborted) {
        task.status = 'aborted'
        task._listeners.forEach(fn => fn('aborted', null))
        task._listeners.clear()
      } else {
        task.status = 'error'
        task.error = err
        task._listeners.forEach(fn => fn('error', err))
        task._listeners.clear()
      }
    })

  _currentTask = task
  return task
}

/**
 * 中止当前任务
 */
export function abortTask() {
  if (_currentTask && _currentTask.status === 'pending') {
    _currentTask.abortController?.abort()
  }
}

/**
 * 获取当前后台任务
 */
export function getTask() {
  return _currentTask
}

/**
 * 清除当前任务
 */
export function clearTask() {
  _currentTask = null
}

/**
 * 订阅任务结果，若已完成则立即回调
 * @param {object} task
 * @param {Function} handler - (type: 'complete'|'error', data) => void
 * @returns {Function} 取消订阅函数
 */
export function subscribe(task, handler) {
  if (!task) return () => {}

  if (task.status === 'completed') {
    handler('complete', task.response)
    return () => {}
  }
  if (task.status === 'error') {
    handler('error', task.error)
    return () => {}
  }
  if (task.status === 'aborted') {
    handler('aborted', null)
    return () => {}
  }

  task._listeners.add(handler)
  return () => task._listeners.delete(handler)
}
