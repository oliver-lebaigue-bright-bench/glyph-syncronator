import { LockKeyhole, WandSparkles } from "lucide-react";
import TopDock from "@/components/TopDock";
import SiteFooter from "@/components/SiteFooter";

const REPOSITORY_URL = "https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator";

export default function Studio() {
  return <div className="product-page studio-page">
    <TopDock repositoryUrl={REPOSITORY_URL} />
    <main className="product-main">
      <section className="studio-hero">
        <div className="studio-panel-grid" aria-hidden="true">{Array.from({ length: 48 }, (_, index) => <i key={index} />)}</div>
        <div className="studio-copy"><div className="studio-mark"><WandSparkles size={18} /><span>Studio</span></div><h1>Draw in<br /><em>light.</em></h1><p>The Glyph animation creator is in development. Soon you will be able to sketch scenes, map them to phone zones, and export them directly to Glyphix.</p><div className="coming-soon"><LockKeyhole size={14} /><span>Coming soon</span></div></div>
      </section>

      <section className="studio-roadmap product-section"><article><span>01</span><h2>Build<br /><em>scenes.</em></h2><p>Compose animation frames directly on a phone-shaped light grid.</p></article><article><span>02</span><h2>Map<br /><em>zones.</em></h2><p>Adjust each available Glyph zone for compatible devices.</p></article><article><span>03</span><h2>Export<br /><em>patterns.</em></h2><p>Send finished animations into future Glyphix presets.</p></article></section>
    </main>
      <SiteFooter />
  </div>;
}
