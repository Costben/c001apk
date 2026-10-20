const CATS = {
  topic: [
    '手机摄影', '摄影', '数码', '耳机', '桌搭', '智能家居', '电脑装机',
    '影像评测', '游戏', '键盘', '汽车', '运动', '咖啡', '旅行',
  ],
  product: ['手机', '平板', '笔记本', '耳机', '手表', '相机', '显示器', '键盘', '音箱', '路由器'],
};

const TAGLINES = {
  手机摄影: '把日常拍成作品',
  摄影: '光影、构图与器材',
  数码: '新机、新玩法',
  耳机: '从百元到万元',
  桌搭: '一块桌面的秩序感',
  智能家居: '让家自己动起来',
  电脑装机: '配置单与实测',
  影像评测: '参数之外的观感',
  游戏: '手感与帧率',
  键盘: '轴体、配列与手感',
  汽车: '智能座舱与智驾',
  运动: '跑步、骑行与装备',
  咖啡: '豆子、器具与手法',
  旅行: '路线与机位',
  手机: '年度旗舰与中端机',
  平板: '生产力还是娱乐',
  笔记本: '轻薄与性能的取舍',
  手表: '健康监测与续航',
  相机: '画幅、镜头与出片',
  显示器: '色准、刷新与尺寸',
  音箱: '听感与房间',
  路由器: '信号与组网',
};

const SLIDE_PAL = [
  ['#2b3a67', '#6c5ce7'],
  ['#12303f', '#2f7d8c'],
  ['#3a1c46', '#8e4a7a'],
  ['#1b2a4a', '#b4405c'],
  ['#103b34', '#4f9b6a'],
  ['#3d2b1f', '#b06a3b'],
  ['#202a44', '#4a6fb5'],
  ['#2d1b2e', '#6a3f7a'],
  ['#132a3a', '#3c8dbc'],
  ['#2a1f3d', '#7d5ba6'],
];

const state = { v: 1, kind: 'topic', cat: 0, popup: false, open: false, theme: 'light' };

const chev = `<svg class="chev" width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M4 6.5 8 10.5l4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const tick = `<svg class="tick" width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="m3.5 8.6 3 3 6-7.2" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/></svg>`;

const cats = () => CATS[state.kind];
const currentCat = () => cats()[state.cat] || cats()[0];

/* ---------------- feed ---------------- */

const HOT = ['12.4万', '8.6万', '6.2万', '4.7万', '3.9万', '2.8万', '2.1万', '1.6万'];
const DISC = ['3.2万', '1.9万', '1.6万', '1.2万', '1.1万', '1.0万', '1.4万', '2.2万'];

const TAGS = {
  topic: ['#手机摄影#', '#随手拍#', '#夜景#', '#构图#', '#人像#', '#扫街#', '#胶片感#', '#长曝光#'],
  product: ['#小米17#', '#iPhone17#', '#一加15#', '#折叠屏#', '#平板推荐#', '#耳机横评#', '#轻薄本#', '#装机单#'],
};

function tagList() {
  const base = TAGS[state.kind];
  const shift = state.cat % base.length;
  const rotated = base.map((_, i) => base[(i + shift) % base.length]);
  return [`#${currentCat()}#`, ...rotated.slice(1)];
}

function sectionHead(text) {
  return `<div class="sechead"><span>${text}</span><svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="m7 4.5 4.5 4.5L7 13.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg></div>`;
}

function topicRow(tag, i) {
  const p = SLIDE_PAL[i % SLIDE_PAL.length];
  return `<article class="card topic">
    <div class="tlogo" style="background:linear-gradient(135deg,${p[0]},${p[1]})">${tag.replace(/#/g, '')[0]}</div>
    <div class="tbody">
      <div class="tname">${tag}</div>
      <div class="tstat">${HOT[i % HOT.length]}热度 · ${DISC[i % DISC.length]}讨论</div>
    </div>
  </article>`;
}

function bannerCard() {
  const p = SLIDE_PAL[state.cat % SLIDE_PAL.length];
  return `<div class="banner">
    <div class="bslide" style="background:linear-gradient(135deg,${p[0]},${p[1]})">
      <div class="btxt">
        <div class="bt">${tagList()[1]}</div>
        <div class="bs">精选话题 · 12.4万热度 · 3.2万讨论</div>
      </div>
    </div>
    <div class="bdots"><i class="on"></i><i></i><i></i></div>
  </div>`;
}

function hcards() {
  const items = [
    ['#耳机横评# 千元档谁最安静', ['#1b2a3a', '#2f5d7c']],
    ['#装机单# 六千预算怎么配', ['#2a1f3d', '#6b4a8a']],
    ['#扫街# 阴天的另一种玩法', ['#132a3a', '#3c8dbc']],
  ];
  return `<div class="hrow">${items
    .map(
      (it) =>
        `<div class="hcard"><div class="im" style="--p1:${it[1][0]};--p2:${it[1][1]}"></div><div class="tt">${it[0]}</div></div>`,
    )
    .join('')}</div>`;
}

function feedHTML(showBanner = true) {
  const tags = tagList();
  return [
    showBanner ? bannerCard() : '',
    sectionHead('热门话题'),
    topicRow(tags[0], 0),
    topicRow(tags[1], 1),
    topicRow(tags[2], 2),
    hcards(),
    sectionHead('大家都在看'),
    topicRow(tags[3], 3),
    topicRow(tags[4], 4),
    topicRow(tags[5], 5),
  ].join('');
}

/* ---------------- headers ---------------- */

function headerV1() {
  return `<div class="v1head">
    <button class="v1btn ${state.popup ? 'up' : ''}" data-act="popup">
      <span class="name">${currentCat()}</span>${chev}
    </button>
    <div class="v1side">${cats().length} 个分类</div>
  </div>`;
}

function popupV1() {
  if (!state.popup) return '';
  return `<div class="scrim" data-act="scrim"></div>
  <div class="popup">
    ${cats()
      .map(
        (c, i) =>
          `<button class="popitem" data-act="cat" data-i="${i}"><span>${c}</span>${i === state.cat ? tick : ''}</button>`,
      )
      .join('')}
  </div>`;
}

function headerV2() {
  return `<div class="chiprail">
    ${cats()
      .map(
        (c, i) => `<button class="chip ${i === state.cat ? 'on' : ''}" data-act="cat" data-i="${i}">${c}</button>`,
      )
      .join('')}
  </div>`;
}

function headerV3() {
  return `<div class="catcard">
    <div class="cathead">
      <span class="catdot"></span>
      <div>
        <div class="catname">${currentCat()}</div>
        <div class="catsub">${cats().length} 个分类 · 共 1.2万条内容</div>
      </div>
      <button class="cattoggle ${state.open ? 'up' : ''}" data-act="expand">${state.open ? '收起' : '展开'}${chev}</button>
    </div>
    <div class="catgrid ${state.open ? 'open' : ''}">
      ${cats()
        .map((c, i) => {
          const p = SLIDE_PAL[i % SLIDE_PAL.length];
          return `<button class="catitem ${i === state.cat ? 'on' : ''}" data-act="cat" data-i="${i}">
            <span class="tile" style="background:linear-gradient(135deg,${p[0]},${p[1]})">${c[0]}</span>
            <span>${c}</span>
          </button>`;
        })
        .join('')}
    </div>
  </div>`;
}

function headerV4() {
  const slides = cats()
    .map((c, i) => {
      const p = SLIDE_PAL[i % SLIDE_PAL.length];
      return `<div class="slide" style="background:linear-gradient(140deg,${p[0]},${p[1]})">
        <div class="txt">
          <div class="kick">分类 ${i + 1} / ${cats().length}</div>
          <div class="big">${c}</div>
          <div class="sub">${TAGLINES[c] || ''}</div>
        </div>
      </div>`;
    })
    .join('');
  const dots = cats()
    .map((_, i) => `<i class="${i === state.cat ? 'on' : ''}" data-act="hero" data-i="${i}"></i>`)
    .join('');
  return `<div class="hero"><div class="herotrack" style="transform:translateX(${-state.cat * 100}%)">${slides}</div></div>
  <div class="dots">${dots}</div>`;
}

function headerV5() {
  return `<div class="lr">
    <div class="rail flat">
      ${cats()
        .map(
          (c, i) =>
            `<button class="ritem ${i === state.cat ? 'on' : ''}" data-act="cat" data-i="${i}">${c}</button>`,
        )
        .join('')}
    </div>
    <div class="scroll">${feedHTML()}</div>
  </div>`;
}

function headerV6() {
  const n = cats().length;
  return `<div class="lr">
    <div class="railcard">
      ${cats()
        .map(
          (c, i) => `<button class="ritem ${i === state.cat ? 'on' : ''}" data-act="cat" data-i="${i}">
            <span class="rname">${c}</span>
            <span class="rcount">${(1.2 + i * 0.37).toFixed(1)}万</span>
          </button>`,
        )
        .join('')}
    </div>
    <div class="scroll">
      <div class="v6head">
        <div class="v6title">${currentCat()}</div>
        <div class="v6sub">${n} 个分类 · 共 ${(1.2 + state.cat * 0.37).toFixed(1)}万条内容</div>
      </div>
      ${feedHTML()}
    </div>
  </div>`;
}

/* ---------------- render ---------------- */

function render() {
  const prevScroll = document.querySelector('.screen.on .scroll');
  const keep = prevScroll ? prevScroll.scrollTop : 0;

  const body = {
    1: () => `<div class="scroll">${headerV1()}${feedHTML()}</div>${popupV1()}`,
    2: () => `<div class="scroll">${headerV2()}${feedHTML()}</div>`,
    3: () => `<div class="scroll">${headerV3()}${feedHTML()}</div>`,
    4: () => `<div class="scroll">${headerV4()}${feedHTML(false)}</div>`,
    5: headerV5,
    6: headerV6,
  };

  const vp = document.getElementById('viewport');
  vp.innerHTML = [1, 2, 3, 4, 5, 6]
    .map(
      (v) => `<section class="screen ${v === state.v ? 'on' : ''}" data-v="${v}">
        ${body[v]()}
      </section>`,
    )
    .join('');

  const sc = document.querySelector('.screen.on .scroll');
  if (sc) sc.scrollTop = keep;

  const chip = document.querySelector('.screen.on .chip.on');
  const rail = document.querySelector('.screen.on .chiprail');
  if (chip && rail) {
    const r = chip.getBoundingClientRect();
    const rr = rail.getBoundingClientRect();
    rail.scrollLeft += r.left - rr.left - (rr.width - r.width) / 2;
  }

  const railItem = document.querySelector('.screen.on .ritem.on');
  const vrail = document.querySelector('.screen.on .rail, .screen.on .railcard');
  if (railItem && vrail) {
    const r = railItem.getBoundingClientRect();
    const rr = vrail.getBoundingClientRect();
    vrail.scrollTop += r.top - rr.top - (rr.height - r.height) / 2;
  }

  bindHero();
}

function bindHero() {
  const hero = document.querySelector('.screen.on .hero');
  if (!hero) return;
  const track = hero.querySelector('.herotrack');
  let startX = 0;
  let dx = 0;
  let dragging = false;

  hero.addEventListener('pointerdown', (e) => {
    dragging = true;
    startX = e.clientX;
    dx = 0;
    track.classList.add('drag');
    hero.setPointerCapture(e.pointerId);
  });

  hero.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    dx = e.clientX - startX;
    const base = -state.cat * hero.clientWidth;
    track.style.transform = `translateX(${base + dx}px)`;
  });

  const end = () => {
    if (!dragging) return;
    dragging = false;
    track.classList.remove('drag');
    const w = hero.clientWidth || 1;
    let next = state.cat;
    if (dx < -w * 0.22) next = Math.min(cats().length - 1, state.cat + 1);
    else if (dx > w * 0.22) next = Math.max(0, state.cat - 1);
    state.cat = next;
    track.style.transform = `translateX(${-next * 100}%)`;
    document
      .querySelectorAll('.screen.on .dots i')
      .forEach((d, i) => d.classList.toggle('on', i === next));
  };

  hero.addEventListener('pointerup', end);
  hero.addEventListener('pointercancel', end);
}

/* ---------------- interactions ---------------- */

document.getElementById('phone').addEventListener('click', (e) => {
  const el = e.target.closest('[data-act]');
  if (!el) return;
  const act = el.dataset.act;
  if (act === 'popup') {
    state.popup = !state.popup;
    render();
  } else if (act === 'scrim') {
    state.popup = false;
    render();
  } else if (act === 'cat') {
    state.cat = Number(el.dataset.i);
    state.popup = false;
    render();
  } else if (act === 'expand') {
    state.open = !state.open;
    render();
  } else if (act === 'hero') {
    state.cat = Number(el.dataset.i);
    render();
  }
});

document.getElementById('tabcapsule').addEventListener('click', (e) => {
  const b = e.target.closest('[data-kind]');
  if (!b) return;
  const k = b.dataset.kind;
  if (k === 'topic' || k === 'product') setKind(k);
});

function setKind(k) {
  if (state.kind === k) return;
  state.kind = k;
  state.cat = 0;
  state.popup = false;
  state.open = false;
  render();
  syncChrome();
}

function setVariant(v) {
  state.v = v;
  state.popup = false;
  render();
  syncChrome();
  history.replaceState(null, '', `#${v}`);
}

function setTheme(t) {
  state.theme = t;
  document.documentElement.dataset.theme = t;
  syncChrome();
}

function syncChrome() {
  document.querySelectorAll('.side-item').forEach((b) => b.classList.toggle('on', Number(b.dataset.v) === state.v));
  document.querySelectorAll('.dock [data-v]').forEach((b) => b.classList.toggle('on', Number(b.dataset.v) === state.v));
  document
    .querySelectorAll('#tabcapsule .seg')
    .forEach((s) => s.classList.toggle('on', s.dataset.kind === state.kind));
  document
    .querySelectorAll('[data-kind-toggle]')
    .forEach((b) => {
      b.textContent =
        b.id === 'dockKind'
          ? state.kind === 'topic'
            ? '数码'
            : '话题'
          : state.kind === 'topic'
            ? '切到「数码」标签'
            : '切回「话题」标签';
    });
}

document.querySelectorAll('.side-item, .dock [data-v]').forEach((b) => {
  b.addEventListener('click', () => setVariant(Number(b.dataset.v)));
});

document.querySelectorAll('[data-theme-toggle]').forEach((b) => {
  b.addEventListener('click', () => setTheme(state.theme === 'light' ? 'dark' : 'light'));
});

document.querySelectorAll('[data-kind-toggle]').forEach((b) => {
  b.addEventListener('click', () => setKind(state.kind === 'topic' ? 'product' : 'topic'));
});

/* ---------------- frame scaling ---------------- */

const phone = document.getElementById('phone');
const frame = document.getElementById('frame');

function fit() {
  const narrow = window.innerWidth <= 900;
  const padX = narrow ? 0 : 32;
  const padY = narrow ? 76 : 32;
  const s = Math.min(1, (window.innerWidth - padX) / 412, (window.innerHeight - padY) / 892);
  phone.style.transform = `scale(${s})`;
  frame.style.width = `${412 * s}px`;
  frame.style.height = `${892 * s}px`;
  document.querySelector('.stage').style.alignItems = narrow ? 'flex-start' : 'center';
}

window.addEventListener('resize', fit);
window.addEventListener('orientationchange', fit);

fit();
const fromHash = Number(location.hash.replace('#', ''));
if (fromHash >= 1 && fromHash <= 6) state.v = fromHash;
render();
syncChrome();
