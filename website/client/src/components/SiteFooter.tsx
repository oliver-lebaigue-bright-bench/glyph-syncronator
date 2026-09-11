import { ArrowUpRight } from "lucide-react";
import { Link } from "wouter";
import {
  BUG_REPORT_URL,
  DISCORD_URL,
  DISCUSSIONS_URL,
  ISSUES_URL,
  LICENSE_URL,
  RELEASES_URL,
  REPOSITORY_URL,
  STATUS_URL,
  STUDIO_NAME,
  STUDIO_URL,
} from "@/lib/site";

const COLUMNS = [
  {
    title: "Product",
    links: [
      { label: "Download", href: "/download", internal: true },
      { label: "Support", href: "/support", internal: true },
      { label: "Studio", href: "/studio", internal: true },
      { label: "Releases", href: RELEASES_URL },
    ],
  },
  {
    title: "Source",
    links: [
      { label: "GitHub", href: REPOSITORY_URL },
      { label: "Issues", href: ISSUES_URL },
      { label: "Report a bug", href: BUG_REPORT_URL },
      { label: "Licence", href: LICENSE_URL },
    ],
  },
  {
    title: "Community",
    links: [
      { label: "Discord", href: DISCORD_URL },
      { label: "Discussions", href: DISCUSSIONS_URL },
      { label: "Service status", href: STATUS_URL },
    ],
  },
];

export default function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer-top">
        <div className="site-footer-brand">
          <img src="/app-icon.svg" alt="" aria-hidden="true" width={34} height={34} />
          <strong>GLYPHIX</strong>
          <p>Music visualiser for the Nothing Phone Glyph interface. Free and open source.</p>
        </div>

        <nav className="site-footer-links" aria-label="Footer">
          {COLUMNS.map((column) => (
            <div key={column.title}>
              <h2>{column.title}</h2>
              <ul>
                {column.links.map((link) => (
                  <li key={link.label}>
                    {link.internal ? (
                      <Link href={link.href}>{link.label}</Link>
                    ) : (
                      <a href={link.href}>{link.label}</a>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
      </div>

      <div className="site-footer-bottom">
        <span>&copy; {new Date().getFullYear()} Glyphix</span>
        <a className="site-footer-credit" href={STUDIO_URL}>
          Built by <strong>{STUDIO_NAME}</strong>
          <ArrowUpRight size={13} aria-hidden="true" />
        </a>
      </div>
    </footer>
  );
}
