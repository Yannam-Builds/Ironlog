import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { applyTheme, useTheme, themeNames } from "./ui/theme";
import {
  asset,
  Button,
  Icon,
  Fox,
  Grade,
  Progress,
  ThemePicker,
} from "./ui/components";
import { BodyMap } from "./ui/BodyMap";
import { Research } from "./research";
import "./styles.css";
import "./landing.css";
const appUrl = `${import.meta.env.BASE_URL}app/`;
const sampleRecovery = {
  Push: 62,
  Pull: 92,
  Legs: 95,
  Arms: 78,
  Core: 94,
  Shoulders: 71,
};
function Demonstration() {
  const [step, setStep] = useState(0);
  const [playing, setPlaying] = useState(false);
  useEffect(() => {
    if (!playing) return;
    const id = setInterval(() => setStep((s) => (s + 1) % 3), 3600);
    return () => clearInterval(id);
  }, [playing]);
  useEffect(() => {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches)
      setPlaying(false);
  }, []);
  return (
    <div className="product-demo">
      <div className="demo-top">
        <span>IronLog in practice</span>
        <span className="sample-label">Sample data</span>
      </div>
      <div
        className="demo-tabs"
        role="group"
        aria-label="Product demonstration"
      >
        {["Workout", "Recovery", "Ledger"].map((label, i) => (
          <button
            key={label}
            aria-pressed={step === i}
            onClick={() => {
              setStep(i);
              setPlaying(false);
            }}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="demo-stage" key={step}>
        {step === 0 ? (
          <>
            <div className="demo-heading">
              <div>
                <span className="eyebrow">Push day</span>
                <h2>One more good set.</h2>
              </div>
              <Icon name="log" size={32} />
            </div>
            <section className="card demo-workout">
              <div className="section-title">
                <h3>Barbell Bench Press</h3>
                <Icon name="more" />
              </div>
              <p>3 × 8 · 120s rest</p>
              {[1, 2, 3].map((n) => (
                <div className={`set-row demo-set set-${n}`} key={n}>
                  <span className="set-number">{n}</span>
                  <strong>60 kg × 8</strong>
                  <span className="demo-check">
                    <Icon name="check" size={18} />
                  </span>
                </div>
              ))}
              <div className="demo-log-button">
                Set saved <Icon name="check" />
              </div>
            </section>
            <div className="demo-foot">
              <Icon name="timer" />
              <span>Rest. Then go again.</span>
            </div>
          </>
        ) : step === 1 ? (
          <>
            <div className="demo-heading">
              <div>
                <span className="eyebrow">After the work</span>
                <h2>Give it time.</h2>
              </div>
              <strong className="demo-score">
                68<small>/100</small>
              </strong>
            </div>
            <div className="demo-maps">
              <BodyMap scores={sampleRecovery} />
              <BodyMap side="back" scores={sampleRecovery} />
            </div>
            <p className="demo-foot">
              Push is recovering. The map is an estimate—not permission to train
              through pain.
            </p>
          </>
        ) : (
          <>
            <div className="demo-heading">
              <div>
                <span className="eyebrow">Iron Ledger</span>
                <h2>Proof, over time.</h2>
              </div>
            </div>
            <div className="demo-grade">
              <Grade grade="Graphite" size={150} />
              <div>
                <h3>Graphite</h3>
                <p>Level 3 · 680 XP</p>
                <Progress value={5} label="Sample Ledger progress" />
              </div>
            </div>
            <div className="demo-milestone">
              <Fox pose="16_proud" />
              <p>
                The grade reflects a training history.
                <br />
                <strong>The next session is yours.</strong>
              </p>
            </div>
          </>
        )}
      </div>
      <div className="demo-control">
        <small>
          A product walkthrough. No sample workouts are added to your log.
        </small>
        <button aria-pressed={playing} onClick={() => setPlaying(!playing)}>
          {playing ? "Pause" : "Play"}
          <span aria-hidden="true"> {playing ? "Ⅱ" : "▷"}</span>
        </button>
      </div>
    </div>
  );
}
function Landing() {
  const theme = useTheme();
  useEffect(() => {
    applyTheme(theme);
  }, [theme]);
  return (
    <>
      <a className="skip-link" href="#content">
        Skip to content
      </a>
      <header className="site-header">
        <a className="brand" href={import.meta.env.BASE_URL}>
          <img src={asset("ironlog-logo.svg")} alt="" />
          IRON<span>LOG</span>
        </a>
        <nav aria-label="Website">
          <a href="#themes">Themes</a>
          <a href="#research">Research</a>
          <a className="nav-open" href={appUrl}>
            Open app <Icon name="next" size={17} />
          </a>
        </nav>
      </header>
      <main id="content">
        <section className="hero">
          <div className="hero-copy">
            <div className="hero-intro">
              <span className="status-dot" />A training log that stays yours.
            </div>
            <h1>
              Train.
              <br />
              Recover.
              <br />
              <span>Prove it.</span>
            </h1>
            <p>
              Put the work on record. Know what you trained, see what needs
              time, and build a history worth coming back to.
            </p>
            <a className="button primary hero-cta" href={appUrl}>
              Try App in web instead <Icon name="next" />
            </a>
            <a
              className="android-link"
              href="https://github.com/Yannam-Builds/Ironlog/releases"
            >
              Get the Android pre-alpha <span aria-hidden="true">↗</span>
            </a>
            <small className="hero-note">
              No account. Local data. Made for the next session.
            </small>
          </div>
          <div className="hero-product">
            <Demonstration />
          </div>
        </section>
        <section className="work-strip">
          <p>
            <strong>Your plan.</strong> Your pace. Your proof.
          </p>
          <div>
            <span>Plans & notes</span>
            <span>Every working set</span>
            <span>Recovery estimates</span>
            <span>The Iron Ledger</span>
          </div>
        </section>
        <section id="themes" className="theme-showcase">
          <div>
            <h2>
              Same IronLog.
              <br />
              <span>Your kind of dark.</span>
              <br />
              Or light.
            </h2>
            <p>
              Twelve palettes from the Android app, down to the surfaces,
              borders, text, and chart colors. Pick one. The whole page changes
              with you.
            </p>
            <p className="theme-selected">
              Now wearing <strong>{themeNames[theme] ?? "Dark"}</strong>
            </p>
            {theme === "monet" && (
              <p className="muted">
                Monet uses IronLog’s fixed fallback palette here. Browsers can’t
                read your wallpaper colors.
              </p>
            )}
          </div>
          <ThemePicker value={theme} onChange={applyTheme} />
        </section>
        <section className="practical">
          <div className="practical-copy">
            <h2>
              For the bit between
              <br />
              “I’ll start” and “done.”
            </h2>
            <p>
              Pick a program. Keep its cues beside each exercise. Log reps,
              load, effort, and notes without losing your place.
            </p>
            <p>
              Warmups wait for you to log them. A paused workout stays
              resumable. A deleted set stays deleted.
            </p>
            <a href={appUrl} className="text-button">
              Start your training log <Icon name="next" />
            </a>
          </div>
          <div className="practical-art">
            <Fox pose="22_dumbbell" />
            <div className="practical-note">
              <span>Forge Fox</span>
              <p>
                A little company.
                <br />
                You do the lifting.
              </p>
            </div>
          </div>
        </section>
        <section className="ownership">
          <div>
            <h2>Your history isn’t a subscription.</h2>
            <p>
              Plans, sessions, measurements, and photos live in this browser.
              Export a complete backup with your photos, or a compatible
              training-data file for Android.
            </p>
            <p>
              No accounts or automatic phone sync. That also means the browser
              is not your backup—keep a copy somewhere safe.
            </p>
          </div>
          <div className="ownership-links">
            <a href={appUrl}>
              Open your log <Icon name="next" />
            </a>
            <a href="#privacy">
              How local storage works <Icon name="next" />
            </a>
          </div>
        </section>
        <section id="install" className="install">
          <div>
            <span className="install-symbol">
              <Icon name="share" size={36} />
            </span>
            <h2>
              Give it a spot
              <br />
              on your Home Screen.
            </h2>
            <p>
              IronLog Web is built for iPhone Safari first. Open it once online,
              then your cached app can open offline.
            </p>
          </div>
          <ol>
            <li>
              <strong>Open the app in Safari.</strong>
              <p>
                Use the web-app button, not a link preview inside another app.
              </p>
            </li>
            <li>
              <strong>Share → Add to Home Screen.</strong>
              <p>Choose Open as Web App if Safari offers it, then tap Add.</p>
            </li>
            <li>
              <strong>Keep one home for your log.</strong>
              <p>
                Safari and the installed app may use separate storage. Export
                and restore a backup if you switch.
              </p>
            </li>
          </ol>
          <p className="install-caveat">
            Keep the screen open for rest-timer feedback. Reliable locked-screen
            alarms, native widgets, Health Connect, and Android on-device AI
            aren’t available in this browser edition.
          </p>
        </section>
        <Research />
        <section id="privacy" className="privacy">
          <h2>Privacy, plainly.</h2>
          <p>
            Your training data stays in this browser’s IndexedDB. This site has
            no analytics, account server, or automatic AI requests. GitHub Pages
            serves public app files and may process ordinary hosting request
            logs. External links have their own privacy policies.
          </p>
          <p>
            The app asks for persistent storage only when you choose it. Storage
            can still be removed, especially in private browsing or when you
            clear website data. Export backups regularly.{" "}
            <a
              href="https://webkit.org/blog/14403/updates-to-storage-policy/"
              target="_blank"
              rel="noreferrer"
            >
              Read WebKit’s storage policy.
            </a>
          </p>
          <h3>Software, font & artwork acknowledgments</h3>
          <p>
            IronLog and its Forge Fox, logo, and grade artwork come from the
            native project. Lexend by Bonnie Shaver-Troup, Thomas Jockin and
            contributors is distributed under the SIL Open Font License. The
            browser app uses React, Vite, TypeScript, Dexie, Zod, fflate, and
            Workbox. Research acknowledgments above are separate from these
            software credits.
          </p>
          <a href={`${import.meta.env.BASE_URL}licenses/IronLog-LICENSE.txt`}>
            IronLog Personal Use License 1.0
          </a>
          <span> · </span>
          <a href={`${import.meta.env.BASE_URL}licenses/Lexend-OFL.txt`}>
            Lexend font license
          </a>
          <span> · </span>
          <a
            href={`${import.meta.env.BASE_URL}licenses/third-party-notices.txt`}
          >
            Software license notices
          </a>
        </section>
      </main>
      <footer className="site-footer">
        <a className="brand" href="#content">
          <img src={asset("ironlog-logo.svg")} alt="" />
          IRON<span>LOG</span>
        </a>
        <p>Train. Recover. Prove it.</p>
        <a href="https://github.com/Yannam-Builds/Ironlog">
          Built in the open ↗
        </a>
      </footer>
    </>
  );
}
createRoot(document.getElementById("root")!).render(<Landing />);
