import {
  AlertCircle,
  ArrowDownRight,
  Check,
  Download as DownloadIcon,
  GitBranch,
  Github,
  Smartphone,
  Vibrate,
} from "lucide-react";
import TopDock from "@/components/TopDock";
import SiteFooter from "@/components/SiteFooter";
import { formatReleaseDate, useReleases, type Release } from "@/hooks/useReleases";
import {
  DEV_ACTIONS_URL,
  DEV_BRANCH,
  DEV_BRANCH_URL,
  DEV_BRANCH_ZIP,
  FALLBACK_OUTPUTS,
  GLYPH_DEVICES,
  LICENSE_URL,
  MIN_ANDROID_VERSION,
  RELEASES_URL,
  REPOSITORY_URL,
} from "@/lib/site";

/** How many releases the stable panel lists. Short of this, it pads with skeletons. */
const RECENT_RELEASES = 3;

function ReleaseRow({ release }: { release: Release }) {
  const primary = release.assets[0];
  return (
    <li className="release-row">
      <div className="release-row-head">
        <a href={release.htmlUrl}>
          {release.title}
          {release.isPrerelease ? <em> (pre-release)</em> : null}
        </a>
        <time dateTime={release.publishedAt}>{formatReleaseDate(release.publishedAt)}</time>
      </div>
      {primary ? (
        <a className="release-row-download" href={primary.downloadUrl}>
          <DownloadIcon size={14} aria-hidden="true" />
          <span>{primary.name}</span>
          <small>
            {primary.architecture} &middot; {primary.size}
          </small>
        </a>
      ) : (
        <p className="release-row-empty">No APK attached to this release.</p>
      )}
    </li>
  );
}

/**
 * Keeps the panel at a stable height before, and after, GitHub has releases to show.
 * Only shimmers while a request is genuinely in flight - an animated placeholder
 * sitting above an error message reads as "still loading" when it is not.
 */
function SkeletonRow({ animated }: { animated: boolean }) {
  return (
    <li className={`release-row is-skeleton ${animated ? "is-loading" : ""}`} aria-hidden="true">
      <div className="release-row-head">
        <span className="skeleton-line skeleton-line-wide" />
        <span className="skeleton-line skeleton-line-narrow" />
      </div>
      <span className="skeleton-line skeleton-line-block" />
    </li>
  );
}

function StableChannel() {
  const state = useReleases();
  const releases = state.status === "ready" ? [state.latest, ...state.archive].slice(0, RECENT_RELEASES) : [];
  const skeletons = Math.max(0, RECENT_RELEASES - releases.length);
  const loading = state.status === "loading";

  return (
    <div className="channel-panel is-stable">
      <span className="channel-tag">Stable</span>
      <h2>{releases[0] ? releases[0].title : "Latest releases"}</h2>
      <p className="channel-lede">
        Signed and tagged builds, recommended for daily use. Version, size and architecture come straight from the
        GitHub release feed.
      </p>

      <ul className="release-rows">
        {releases.map((release) => (
          <ReleaseRow key={release.id} release={release} />
        ))}
        {Array.from({ length: skeletons }, (_, index) => (
          <SkeletonRow key={`skeleton-${index}`} animated={loading} />
        ))}
      </ul>

      {!loading && state.status !== "ready" ? (
        <p className="release-status-inline" role="status">
          <AlertCircle size={15} aria-hidden="true" />
          <span>
            {state.status === "empty"
              ? "No release has been published yet. These slots fill in on their own once one goes up."
              : `Could not read the release feed. ${state.message}`}
          </span>
        </p>
      ) : null}

      <a className="channel-foot-link" href={RELEASES_URL}>
        All releases on GitHub <ArrowDownRight size={14} aria-hidden="true" />
      </a>
    </div>
  );
}

/** The dev channel is the working branch — no tags, no guarantees. */
function DevChannel() {
  return (
    <div className="channel-panel is-dev">
      <span className="channel-tag">Dev channel</span>
      <h2>Build from {DEV_BRANCH}</h2>
      <p className="channel-lede">
        The <code>{DEV_BRANCH}</code> branch is where work lands before it is tagged. There is no prebuilt APK here —
        expect breakage, and do not use it as your daily build.
      </p>

      <ol className="dev-steps">
        <li>
          <GitBranch size={14} aria-hidden="true" />
          <span>
            Clone the branch, or grab it as a zip, then build it with <code>./gradlew assembleDebug</code>.
          </span>
        </li>
        <li>
          <DownloadIcon size={14} aria-hidden="true" />
          <span>If CI is publishing artifacts, the newest build is attached to the most recent run under Actions.</span>
        </li>
      </ol>

      <div className="dev-links">
        <a href={DEV_BRANCH_URL}>
          Browse {DEV_BRANCH} <ArrowDownRight size={14} aria-hidden="true" />
        </a>
        <a href={DEV_BRANCH_ZIP}>
          Source zip <ArrowDownRight size={14} aria-hidden="true" />
        </a>
        <a href={DEV_ACTIONS_URL}>
          CI artifacts <ArrowDownRight size={14} aria-hidden="true" />
        </a>
      </div>
    </div>
  );
}

export default function Download() {
  return (
    <div className="product-page">
      <TopDock repositoryUrl={REPOSITORY_URL} />
      <main className="product-main">
        <section className="product-hero product-hero-utility is-bare">
          <h1>Download Glyphix</h1>
        </section>

        <section className="product-section channel-grid-2" aria-label="Build channels">
          <StableChannel />
          <DevChannel />
        </section>

        <section className="product-section supported" aria-labelledby="supported-title">
          <h2 id="supported-title">Supported phones</h2>
          <p className="section-lede">
            Glyphix needs {MIN_ANDROID_VERSION} or newer. Per-zone Glyph mapping needs Nothing hardware; everything else
            runs on any Android phone.
          </p>

          <div className="support-tiers">
            <article className="support-tier is-primary">
              <header>
                <Smartphone size={20} aria-hidden="true" />
                <div>
                  <h3>Full Glyph mapping</h3>
                  <p>Individual LED zones follow individual frequency bands.</p>
                </div>
                <span className="tier-badge">Nothing only</span>
              </header>
              <ul className="check-list">
                {GLYPH_DEVICES.map((device) => (
                  <li key={device.name}>
                    <Check size={15} aria-hidden="true" />
                    <span>{device.name}</span>
                    <small>{device.zones}</small>
                  </li>
                ))}
              </ul>
            </article>

            <article className="support-tier is-secondary">
              <header>
                <Vibrate size={20} aria-hidden="true" />
                <div>
                  <h3>Haptics &amp; flashlight</h3>
                  <p>No Glyph interface required. The phone still reacts to the music.</p>
                </div>
                <span className="tier-badge">Any Android</span>
              </header>
              <ul className="check-list">
                {FALLBACK_OUTPUTS.map((output) => (
                  <li key={output.name}>
                    <Check size={15} aria-hidden="true" />
                    <span>{output.name}</span>
                    <small>{output.detail}</small>
                  </li>
                ))}
              </ul>
              <p className="tier-note">
                Everything except per-zone lighting works here, including beat detection and the full analysis engine.
              </p>
            </article>
          </div>
        </section>

        <section className="product-section source-strip" aria-labelledby="source-title">
          <div className="source-strip-copy">
            <h2 id="source-title">Build it yourself</h2>
            <p>
              The full Android source is public. Clone it, read it, or compile your own APK. Redistribution terms are in
              the <a className="inline-link" href={LICENSE_URL}>LICENCE</a> file.
            </p>
          </div>
          <a className="source-link" href={REPOSITORY_URL}>
            <Github size={19} aria-hidden="true" /> View on GitHub <ArrowDownRight size={17} aria-hidden="true" />
          </a>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
