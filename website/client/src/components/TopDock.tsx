import { Github, Menu, X } from "lucide-react";
import { useState, type CSSProperties } from "react";
import { Link, useLocation } from "wouter";

const navigationItems = [
  { href: "/support", label: "Support", number: "01" },
  { href: "/download", label: "Download", number: "02" },
  { href: "/studio", label: "Studio", number: "03" },
];

function GlyphMark() {
  return <img className="dock-logo" src="/app-icon.svg" alt="" aria-hidden="true" width={26} height={26} />;
}

export default function TopDock({ repositoryUrl }: { repositoryUrl: string }) {
  const [location] = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  const renderItem = (item: typeof navigationItems[number], mobile = false) => {
    const isActive = location === item.href;
    const itemStyle = { "--dock-width": mobile ? "100%" : "92px" } as CSSProperties;
    return <Link
      key={item.href}
      href={item.href}
      className={`dock-link ${isActive ? "is-active" : ""} ${mobile ? "dock-mobile-link" : ""}`}
      style={itemStyle}
      onClick={() => setMenuOpen(false)}
      aria-current={isActive ? "page" : undefined}
    >
      <span className="dock-index">{item.number}</span>
      <span className="dock-label">{item.label}</span>
      <span className="dock-active-pulse" aria-hidden="true" />
    </Link>;
  };

  return <header className="top-dock-header">
    <nav className="top-dock" aria-label="Main navigation">
      <Link className="dock-brand" href="/" onClick={() => setMenuOpen(false)} aria-label="Glyphix landing page">
        <GlyphMark />
        <span>GLYPHIX</span>
      </Link>
      <div className="dock-items">{navigationItems.map((item) => renderItem(item))}</div>
      <button
        className="dock-mobile-toggle"
        onClick={() => setMenuOpen((current) => !current)}
        aria-label="Toggle navigation menu"
        aria-expanded={menuOpen}
      >
        {menuOpen ? <X size={18} /> : <Menu size={18} />}
      </button>
    </nav>

    <div className={`dock-mobile-menu ${menuOpen ? "is-open" : ""}`} aria-label="Mobile navigation">
      <div className="dock-mobile-menu-head"><span>Navigate</span><b>Glyphix</b></div>
      <div>{navigationItems.map((item) => renderItem(item, true))}</div>
    </div>

    <div className="dock-utilities">
      <a className="dock-utility dock-github" href={repositoryUrl} target="_blank" rel="noreferrer" aria-label="Open the Glyphix GitHub repository"><Github size={16} /></a>
    </div>
  </header>;
}
