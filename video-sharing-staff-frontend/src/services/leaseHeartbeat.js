export async function runLeaseHeartbeat({
  request,
  isCurrent,
  onSuccess,
  onConflict,
  onFailure,
}) {
  try {
    const response = await request()
    if (!isCurrent()) return
    onSuccess(response)
  } catch (error) {
    if (!isCurrent()) return
    if (error.response?.status === 409) {
      onConflict(error)
      return
    }
    onFailure(error)
  }
}
