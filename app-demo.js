const demoState = {
  tab: "home",
  readiness: 92,
  streak: 7,
  xp: 84,
  level: 8,
  grade: "Iron",
  theme: "amethyst",
};

const foxAssets = {
  determined: "assets/site/forgefox_determined.png",
  proud: "assets/site/forgefox_proud.png",
  dumbbell: "assets/site/forgefox_dumbbell.png",
  streak: "assets/site/forgefox_streak.png",
  watch: "assets/site/forgefox_watch.png",
};

const screens = {
  home: {
    kicker: "Daily proof",
    title: "Ready to train",
    metricTitle: "Fresh",
    metricCopy: "Push volume today. Ledger proof is available.",
    fox: "determined",
    render: () => `
      <article class="app-card">
        <h3>Recommended plan</h3>
        <p>Upper strength block. Bench press, weighted pullups, row volume.</p>
        <div class="progress-line" style="--value: ${demoState.xp}%"><span></span></div>
      </article>
      <div class="mini-grid">
        <article class="mini-card">
          <h3>${demoState.streak} day streak</h3>
          <p>Proof logged before midnight keeps the chain alive.</p>
        </article>
        <article class="mini-card">
          <h3>Level ${demoState.level}</h3>
          <p>${demoState.grade} candidate. ${demoState.xp}% to next gate.</p>
        </article>
      </div>
      <article class="app-card">
        <h3>Muscle recovery</h3>
        <p>Chest and back are above 90%. Legs are maintaining.</p>
        <div class="progress-line" style="--value: ${demoState.readiness}%"><span></span></div>
      </article>
    `,
  },
  log: {
    kicker: "Workout log",
    title: "Upper strength",
    metricTitle: "Set quality",
    metricCopy: "Every completed set updates proof, XP, and recovery.",
    fox: "dumbbell",
    render: () => `
      <article class="app-card">
        <h3>Bench press</h3>
        <div class="lift-row"><strong>Set 1</strong><span>65 kg x 8</span><span>Done</span></div>
        <div class="lift-row"><strong>Set 2</strong><span>67.5 kg x 6</span><span>Next</span></div>
        <button class="set-button" type="button" data-inline-complete>Complete current set</button>
      </article>
      <article class="app-card">
        <h3>Progression</h3>
        <p>Recommended next target: add 2.5 kg if RPE stays below 8.</p>
        <div class="progress-line" style="--value: ${Math.min(100, demoState.xp + 8)}%"><span></span></div>
      </article>
    `,
  },
  recovery: {
    kicker: "Recovery map",
    title: "Train or recover",
    metricTitle: "Readiness",
    metricCopy: "Move the slider to preview how the app reacts to recovery state.",
    fox: "watch",
    render: () => `
      <article class="app-card">
        <h3>Manual check-in</h3>
        <p>Energy, soreness, and sleep adjust the recommendation.</p>
        <div class="range-row">
          <input data-readiness-slider type="range" min="45" max="99" value="${demoState.readiness}" aria-label="Recovery readiness" />
          <strong data-slider-value>${demoState.readiness}</strong>
        </div>
      </article>
      <div class="body-map" aria-label="Recovery body map preview">
        <div class="body-figure" title="Front recovery preview"></div>
        <div class="body-figure" title="Back recovery preview"></div>
      </div>
    `,
  },
  ledger: {
    kicker: "Iron Ledger",
    title: "Proof trail",
    metricTitle: `${demoState.grade} grade`,
    metricCopy: "Rank, badges, and integrity follow verified training history.",
    fox: "proud",
    render: () => `
      <article class="app-card ledger-preview">
        <img src="assets/site/iron_grade_iron.png" alt="Iron grade badge" />
        <div>
          <h3>Level ${demoState.level}</h3>
          <p>Integrity 100%. ${demoState.xp}% through the current gate.</p>
          <div class="progress-line" style="--value: ${demoState.xp}%"><span></span></div>
        </div>
      </article>
      <article class="app-card">
        <h3>Training signals</h3>
        ${["STR", "PWR", "HYP", "END"].map((signal, index) => `
          <div class="signal-meter">
            <strong>${signal}</strong>
            <div class="progress-line" style="--value: ${Math.min(98, demoState.xp - index * 7)}%"><span></span></div>
            <span>${demoState.level + index}</span>
          </div>
        `).join("")}
      </article>
    `,
  },
};

const els = {
  body: document.body,
  content: document.querySelector("[data-demo-content]"),
  screenTitle: document.querySelector("[data-screen-title]"),
  screenKicker: document.querySelector("[data-screen-kicker]"),
  screenFox: document.querySelector("[data-screen-fox]"),
  readiness: document.querySelector("[data-readiness]"),
  metricTitle: document.querySelector("[data-metric-title]"),
  metricCopy: document.querySelector("[data-metric-copy]"),
  tabs: [...document.querySelectorAll("[data-demo-tab]")],
  themeButtons: [...document.querySelectorAll("[data-theme-choice]")],
};

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function setTab(tab) {
  demoState.tab = tab;
  els.tabs.forEach((button) => button.classList.toggle("is-active", button.dataset.demoTab === tab));
  renderDemo(true);
}

function setFox(assetKey) {
  if (!els.screenFox) return;

  els.screenFox.src = foxAssets[assetKey] || foxAssets.determined;
  els.screenFox.classList.remove("is-jumping");
  requestAnimationFrame(() => {
    els.screenFox.classList.add("is-jumping");
    window.setTimeout(() => els.screenFox.classList.remove("is-jumping"), 430);
  });
}

function renderDemo(animate = false) {
  const screen = screens[demoState.tab];
  if (!screen || !els.content) return;

  const doRender = () => {
    els.screenKicker.textContent = screen.kicker;
    els.screenTitle.textContent = screen.title;
    els.readiness.textContent = String(demoState.readiness);
    els.metricTitle.textContent = screen.metricTitle;
    els.metricCopy.textContent = screen.metricCopy;
    els.content.innerHTML = screen.render();
    setFox(screen.fox);
    bindInlineControls();
  };

  if (!animate) {
    doRender();
    return;
  }

  els.content.classList.add("is-swapping");
  window.setTimeout(() => {
    doRender();
    els.content.classList.remove("is-swapping");
  }, 150);
}

function completeSet() {
  demoState.xp = clamp(demoState.xp + 6, 0, 100);
  demoState.readiness = clamp(demoState.readiness - 3, 45, 99);
  if (demoState.xp >= 100) {
    demoState.level += 1;
    demoState.xp = 18;
    demoState.grade = demoState.level >= 10 ? "Steel" : "Iron";
    setTab("ledger");
    return;
  }
  renderDemo(true);
}

function bindInlineControls() {
  const inlineComplete = document.querySelector("[data-inline-complete]");
  if (inlineComplete) {
    inlineComplete.addEventListener("click", completeSet);
  }

  const slider = document.querySelector("[data-readiness-slider]");
  const sliderValue = document.querySelector("[data-slider-value]");
  if (slider && sliderValue) {
    slider.addEventListener("input", (event) => {
      demoState.readiness = Number(event.target.value);
      sliderValue.textContent = String(demoState.readiness);
      els.readiness.textContent = String(demoState.readiness);
      els.metricTitle.textContent = demoState.readiness >= 82 ? "Fresh" : demoState.readiness >= 66 ? "Maintain" : "Back off";
      els.metricCopy.textContent = demoState.readiness >= 82
        ? "Good window for heavier work."
        : demoState.readiness >= 66
          ? "Keep quality high and volume controlled."
          : "Recovery circuit is the better move.";
    });
  }
}

function revealVisibleSections() {
  document.querySelectorAll(".reveal").forEach((element) => element.classList.add("is-visible"));
}

function setupRevealObserver() {
  const revealElements = [...document.querySelectorAll(".reveal")];

  if (!revealElements.length) return;

  if (!("IntersectionObserver" in window)) {
    revealVisibleSections();
    return;
  }

  const revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          revealObserver.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.18 }
  );

  revealElements.forEach((element) => revealObserver.observe(element));
}

document.querySelectorAll("[data-demo-tab]").forEach((button) => {
  button.addEventListener("click", () => setTab(button.dataset.demoTab));
});

document.querySelectorAll("[data-quick-action]").forEach((button) => {
  button.addEventListener("click", () => {
    const action = button.dataset.quickAction;
    if (action === "complete") {
      completeSet();
    }
    if (action === "risk") {
      demoState.readiness = 61;
      demoState.streak = Math.max(1, demoState.streak);
      setTab("recovery");
      els.metricTitle.textContent = "Streak risk";
      els.metricCopy.textContent = "A short proof session keeps the daily chain alive.";
      setFox("watch");
    }
    if (action === "level") {
      demoState.level += 1;
      demoState.xp = 12;
      demoState.grade = demoState.level >= 10 ? "Steel" : "Iron";
      setTab("ledger");
      setFox("proud");
    }
  });
});

els.themeButtons.forEach((button) => {
  button.addEventListener("click", () => {
    demoState.theme = button.dataset.themeChoice;
    document.documentElement.dataset.siteTheme = demoState.theme;
    els.themeButtons.forEach((themeButton) => {
      themeButton.classList.toggle("is-active", themeButton === button);
    });
  });
});

try {
  renderDemo();
  setupRevealObserver();
} catch (error) {
  revealVisibleSections();
  console.error("IronLog demo failed to initialize", error);
}