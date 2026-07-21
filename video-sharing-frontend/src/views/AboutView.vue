<template>
  <section class="about-view" aria-labelledby="about-title">
    <header class="about-view__hero">
      <p class="about-view__eyebrow">VISION / ABOUT</p>
      <h1 id="about-title">关于视界</h1>
      <p class="about-view__lead">发现每一种热爱，分享每一刻灵感。</p>
      <p class="about-view__description">视界是一个专注于发现、分享与沉浸观看的视频分享体验。</p>
    </header>

    <div class="about-view__cards" aria-label="视界能力">
      <article class="about-view__card">
        <span class="about-view__index">01</span>
        <h2>发现内容</h2>
        <p>从新鲜灵感到长期热爱，找到值得停留的每一帧。</p>
      </article>
      <article class="about-view__card">
        <span class="about-view__index">02</span>
        <h2>分享创作</h2>
        <p>让每一次记录都有被看见的机会，也让热爱自然相遇。</p>
      </article>
      <article class="about-view__card">
        <span class="about-view__index">03</span>
        <h2>专注观看</h2>
        <p>用干净、沉浸的观看体验，把注意力留给真正喜欢的内容。</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.about-view { margin: 0 auto; max-width: 1120px; padding: clamp(24px, 5vw, 72px) 0; }
.about-view__hero { max-width: 720px; }
.about-view__eyebrow { color: var(--brand); font-size: .72rem; font-weight: 800; letter-spacing: .15em; margin: 0 0 10px; }
h1 { font-size: clamp(2.4rem, 6vw, 5rem); letter-spacing: -.065em; line-height: 1.04; margin: 0; }
.about-view__lead { font-size: clamp(1.15rem, 2vw, 1.45rem); font-weight: 700; margin: 20px 0 6px; }
.about-view__description { color: var(--muted); margin: 0; max-width: 560px; }
.about-view__cards { display: grid; gap: 18px; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: clamp(36px, 6vw, 72px); }
.about-view__card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); min-height: 230px; padding: 24px; }
.about-view__index { color: var(--brand); font-size: .78rem; font-weight: 800; letter-spacing: .1em; }
h2 { font-size: 1.25rem; margin: 44px 0 8px; }
.about-view__card p { color: var(--muted); margin: 0; }
@media (max-width: 800px) { .about-view__cards { grid-template-columns: 1fr; } .about-view__card { min-height: auto; } h2 { margin-top: 24px; } }
</style>
