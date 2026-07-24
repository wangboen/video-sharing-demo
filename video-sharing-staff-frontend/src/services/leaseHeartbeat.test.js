import { describe, expect, it, vi } from 'vitest'
import { runLeaseHeartbeat } from './leaseHeartbeat'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('lease heartbeat generation guard', () => {
  it('ignores a late success after a same-video-ID lease generation is replaced', async () => {
    const firstToken = { videoId: 42 }
    const secondToken = { videoId: 42 }
    let currentToken = firstToken
    const request = deferred()
    const onSuccess = vi.fn()
    const operation = runLeaseHeartbeat({
      request: () => request.promise,
      isCurrent: () => currentToken === firstToken,
      onSuccess,
      onConflict: vi.fn(),
      onFailure: vi.fn(),
    })

    currentToken = secondToken
    request.resolve({ data: { id: 42, version: 99 } })
    await operation

    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('ignores a late conflict after a same-video-ID lease generation is replaced', async () => {
    const firstToken = { videoId: 42 }
    const secondToken = { videoId: 42 }
    let currentToken = firstToken
    const request = deferred()
    const onConflict = vi.fn()
    const operation = runLeaseHeartbeat({
      request: () => request.promise,
      isCurrent: () => currentToken === firstToken,
      onSuccess: vi.fn(),
      onConflict,
      onFailure: vi.fn(),
    })

    currentToken = secondToken
    request.reject({ response: { status: 409 } })
    await operation

    expect(onConflict).not.toHaveBeenCalled()
  })
})
