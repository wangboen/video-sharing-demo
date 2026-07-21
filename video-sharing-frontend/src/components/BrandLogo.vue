<template>
  <span class="brand-logo" aria-label="视界">
    <svg class="brand-logo__mark" viewBox="0 0 40 40" aria-hidden="true">
      <rect width="40" height="40" rx="13" fill="url(#brand-gradient)" />
      <path d="M16 12.75 28 20 16 27.25V12.75Z" fill="white" />
      <defs>
        <linearGradient id="brand-gradient" x1="6" y1="5" x2="34" y2="35" gradientUnits="userSpaceOnUse">
          <stop stop-color="#ff304f" />
          <stop offset="1" stop-color="#ff6b35" />
        </linearGradient>
      </defs>
    </svg>
    <span class="brand-logo__wordmark">视界</span>
  </span>
</template>

<style scoped>
.brand-logo {
  align-items: center;
  color: var(--text);
  display: inline-flex;
  gap: 10px;
  white-space: nowrap;
}

.brand-logo__mark {
  display: block;
  height: 36px;
  width: 36px;
}

.brand-logo__wordmark {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
</style>
