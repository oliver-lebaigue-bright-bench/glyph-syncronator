import { useMemo, useState } from "react";
import {
  ArrowUpRight,
  Activity,
  Bug,
  ChevronDown,
  Code2,
  Lightbulb,
  MessagesSquare,
  Palette,
  Search,
  Sparkles,
} from "lucide-react";
import TopDock from "@/components/TopDock";
import SiteFooter from "@/components/SiteFooter";
import { useServiceStatus } from "@/hooks/useRepoStatus";
import {
  BUG_REPORT_URL,
  DISCORD_URL,
  DISCUSSIONS_URL,
  FEATURE_REQUEST_URL,
  ISSUES_URL,
  REPOSITORY_URL,
  STATUS_URL,
} from "@/lib/site";

type Faq = { question: string; answer: string };
type FaqCategory = { id: string; title: string; blurb: string; items: Faq[] };

const FAQ_CATEGORIES: FaqCategory[] = [
  {
    id: "setup",
    title: "Setup & connection",
    blurb: "Getting the app installed, permitted, and listening.",
    items: [
      {
        question: "Which permissions does Glyphix actually need?",
        answer:
          "Two. Notification listener access, so it can see what is playing, and audio capture, so it can read the output signal. Both are requested on first launch. If you skipped them, they are in Settings, then Permissions.",
      },
      {
        question: "Does Glyphix work with Spotify, YouTube Music and other streaming apps?",
        answer:
          "Yes. Glyphix reads the audio output of the device rather than a specific app's library, so anything playing through the speaker or headphones works, including Spotify, YouTube Music, Apple Music and SoundCloud.",
      },
      {
        question: "My Nothing phone is not in the supported list. Can I still use it?",
        answer:
          "You can. Haptics and flashlight modes run on any Android device without a Glyph interface, so you still get music-driven vibration and flash. Only per-zone Glyph mapping is limited to the listed models.",
      },
      {
        question: "Is Glyphix open source?",
        answer:
          "Yes. The full Android source is on GitHub. You are welcome to read it, open issues, or send pull requests. Commercial redistribution needs permission, which is set out in the LICENSE file.",
      },
    ],
  },
  {
    id: "glyph-api",
    title: "Glyph API & Nothing OS",
    blurb: "How zone mapping talks to the Glyph interface.",
    items: [
      {
        question: "How do I map a frequency band to a specific Glyph zone?",
        answer:
          "Open Zone Mapping. Every zone can be assigned independently: bass, kick, low, mid, high and air. Drag a band onto a zone or pick one of the preset layouts. Changes apply live while a track is playing.",
      },
      {
        question: "Why do I have fewer zones than someone else with a Nothing phone?",
        answer:
          "Zone count is a hardware property. Phone (1) exposes five zones and Phone (2) exposes eleven, while the (2a), (3a) and (4a) families expose three. Glyphix reads the count from the Glyph interface and only offers mappings your device can actually light.",
      },
      {
        question: "Does a Nothing OS update break Glyphix?",
        answer:
          "It can. The Glyph interface is versioned by Nothing, and a major OS release occasionally changes it. If zones stop responding right after a system update, check for a Glyphix update before filing a bug.",
      },
      {
        question: "Can Glyphix run at the same time as the built-in Glyph features?",
        answer:
          "Not simultaneously. Android grants Glyph control to one owner at a time. Glyphix releases control during calls and system notifications so the phone can still signal you, then takes it back afterwards.",
      },
    ],
  },
  {
    id: "sync",
    title: "Audio sync & latency",
    blurb: "Timing between what you hear and what you see.",
    items: [
      {
        question: "The lights lag slightly behind the music. Can I fix that?",
        answer:
          "Yes. Bluetooth adds output latency that varies by codec and by headphones, typically between 100 and 300 milliseconds. Settings, then Sync offset, lets you nudge the visual timing until it lines up with what you hear.",
      },
      {
        question: "Why is the response softer on quiet tracks?",
        answer:
          "Glyphix maps the real signal level rather than normalising everything to full brightness. A quiet master produces quiet lighting. If you would rather have a consistent range, turn on auto-gain in Settings, then Analysis.",
      },
      {
        question: "How often does the analysis update?",
        answer:
          "The analyser runs on a short window and drives output at up to 60 updates per second. Lowering the analysis framerate in Settings, then Performance, reduces battery use at the cost of a slightly coarser response.",
      },
    ],
  },
  {
    id: "troubleshooting",
    title: "Troubleshooting",
    blurb: "When something is clearly wrong.",
    items: [
      {
        question: "The Glyph lights are not responding to music at all.",
        answer:
          "Check that both notification listener and audio permissions are granted, then toggle the service off and back on from the main screen. If the phone is in battery saver, Android may be suspending the service in the background.",
      },
      {
        question: "The app crashes on launch.",
        answer:
          "That is usually a corrupted settings file. In Android's app info for Glyphix, tap Clear cache and relaunch. If it still crashes, use Clear data, which resets your zone presets but reliably clears the bad state.",
      },
      {
        question: "Battery drains faster than I expected.",
        answer:
          "Continuous audio analysis costs power. Lower the analysis framerate in Settings, then Performance, or enable Background power saver, which halves the refresh rate while the screen is off.",
      },
      {
        question: "Output pauses whenever I take a call.",
        answer:
          "That is deliberate. Glyphix hands Glyph control back to the system during calls so incoming call signalling still works, and resumes on its own when the call ends.",
      },
    ],
  },
];

const ROLES = [
  { icon: Code2, title: "Android engineers", detail: "Kotlin, the Glyph SDK, and low-latency audio analysis." },
  { icon: Palette, title: "Designers", detail: "Interface and motion work for the app and this site." },
  { icon: Sparkles, title: "Testers", detail: "Any Nothing device, any Android phone. Break it and tell us how." },
];

function FaqItem({ faq, categoryId }: { faq: Faq; categoryId: string }) {
  const [open, setOpen] = useState(false);
  const panelId = `${categoryId}-${faq.question.slice(0, 24).replace(/\W+/g, "-").toLowerCase()}`;

  return (
    <article className={`faq-item ${open ? "is-open" : ""}`}>
      <h3>
        <button
          type="button"
          className="faq-trigger"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => setOpen((current) => !current)}
        >
          <span>{faq.question}</span>
          <ChevronDown size={16} className="faq-chevron" aria-hidden="true" />
        </button>
      </h3>
      <div className="faq-body" id={panelId} hidden={!open}>
        <p>{faq.answer}</p>
      </div>
    </article>
  );
}

function StatusBanner() {
  const service = useServiceStatus(STATUS_URL);

  const label =
    service.state === "checking" ? "Checking" : service.state === "operational" ? "Responding" : "Not responding";

  return (
    <a className={`status-banner is-${service.state}`} href={STATUS_URL}>
      <Activity size={18} aria-hidden="true" />
      <p>
        <strong>Having a problem?</strong> Check the status page before you file a bug — it may already be a known
        outage.
      </p>
      <span className="status-pill">
        <i aria-hidden="true" />
        {label}
      </span>
      <span className="status-banner-cta">
        Status page <ArrowUpRight size={14} aria-hidden="true" />
      </span>
    </a>
  );
}

export default function Support() {
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return FAQ_CATEGORIES;
    return FAQ_CATEGORIES.map((category) => ({
      ...category,
      items: category.items.filter(
        (item) => item.question.toLowerCase().includes(needle) || item.answer.toLowerCase().includes(needle),
      ),
    })).filter((category) => category.items.length > 0);
  }, [query]);

  const resultCount = filtered.reduce((total, category) => total + category.items.length, 0);
  const totalCount = FAQ_CATEGORIES.reduce((total, category) => total + category.items.length, 0);

  return (
    <div className="product-page">
      <TopDock repositoryUrl={REPOSITORY_URL} />
      <main className="product-main">
        <section className="product-hero product-hero-utility">
          <h1>Support</h1>
          <p>
            Get help, report something broken, or check whether the services are up. Everything here goes straight to
            the people working on Glyphix.
          </p>
        </section>

        <StatusBanner />

        <section className="product-section channels" aria-labelledby="channels-title">
          <h2 id="channels-title">Where to go</h2>
          <div className="channel-grid">
            <a className="channel-card" href={DISCORD_URL}>
              <MessagesSquare size={22} aria-hidden="true" />
              <strong>Community chat</strong>
              <p>The fastest route to an answer. Ask a question, compare zone mappings, or see if someone has already hit your problem.</p>
              <span className="channel-cta">
                Open Discord <ArrowUpRight size={14} aria-hidden="true" />
              </span>
            </a>

            <div className="channel-card channel-card-split">
              <Bug size={22} aria-hidden="true" />
              <strong>Issue tracker</strong>
              <p>Reproducible fault? File a bug with your device and Nothing OS version. Got an idea instead? Send it to the feature queue.</p>
              <div className="channel-split-links">
                <a href={BUG_REPORT_URL}>
                  <Bug size={13} aria-hidden="true" /> Report a bug
                </a>
                <a href={FEATURE_REQUEST_URL}>
                  <Lightbulb size={13} aria-hidden="true" /> Request a feature
                </a>
                <a href={ISSUES_URL}>
                  Browse all issues <ArrowUpRight size={13} aria-hidden="true" />
                </a>
              </div>
            </div>


            <a className="channel-card" href={DISCUSSIONS_URL}>
              <MessagesSquare size={22} aria-hidden="true" />
              <strong>GitHub Discussions</strong>
              <p>Longer questions and design ideas, kept searchable next to the source rather than scrolling away in chat.</p>
              <span className="channel-cta">
                Open discussions <ArrowUpRight size={14} aria-hidden="true" />
              </span>
            </a>
          </div>
        </section>

        <section className="product-section knowledge-base" aria-labelledby="kb-title">
          <div className="kb-head">
            <h2 id="kb-title">Common questions</h2>
            <div className="kb-search">
              <Search size={16} aria-hidden="true" />
              <input
                type="search"
                value={query}
                placeholder="Search questions and answers"
                aria-label="Search the knowledge base"
                onChange={(event) => setQuery(event.target.value)}
              />
            </div>
          </div>

          <p className="kb-count" role="status">
            {query.trim()
              ? `${resultCount} ${resultCount === 1 ? "result" : "results"} for "${query.trim()}"`
              : `${totalCount} questions across ${FAQ_CATEGORIES.length} categories`}
          </p>

          {filtered.length > 0 ? (
            filtered.map((category) => (
              <section className="kb-category" key={category.id} aria-labelledby={`${category.id}-title`}>
                <div className="kb-category-head">
                  <h3 id={`${category.id}-title`}>{category.title}</h3>
                  <p>{category.blurb}</p>
                </div>
                <div className="faq-list">
                  {category.items.map((faq) => (
                    <FaqItem key={faq.question} faq={faq} categoryId={category.id} />
                  ))}
                </div>
              </section>
            ))
          ) : (
            <div className="kb-empty">
              <p>
                Nothing matches <strong>{query.trim()}</strong>.
              </p>
              <a className="action-button action-quiet" href={DISCORD_URL}>
                Ask on Discord <ArrowUpRight size={15} aria-hidden="true" />
              </a>
            </div>
          )}
        </section>

        <section className="product-section hiring" aria-labelledby="hiring-title">
          <div className="hiring-copy">
            <h2 id="hiring-title">
              Come build <em>Glyphix.</em>
            </h2>
            <p>
              Glyphix is built in the open by a small group of people who like making phones do things they were not
              quite meant to do. There is no formal application. Join the Discord, say what you are good at, and pick
              something up.
            </p>
            <a className="action-button action-primary" href={DISCORD_URL}>
              Join the Discord <ArrowUpRight size={16} aria-hidden="true" />
            </a>
          </div>
          <ul className="hiring-roles">
            {ROLES.map((role) => (
              <li key={role.title}>
                <role.icon size={18} aria-hidden="true" />
                <div>
                  <strong>{role.title}</strong>
                  <p>{role.detail}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
