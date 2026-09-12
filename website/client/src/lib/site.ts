// Single source of truth for outbound links and product facts.
// These strings were previously duplicated across Home, Download and Support.

export const REPO_OWNER = "oliver-lebaigue-bright-bench";
export const REPO_NAME = "glyph-syncronator";

export const REPOSITORY_URL = `https://github.com/${REPO_OWNER}/${REPO_NAME}`;
export const RELEASES_URL = `${REPOSITORY_URL}/releases`;
export const ISSUES_URL = `${REPOSITORY_URL}/issues`;
export const DISCUSSIONS_URL = `${REPOSITORY_URL}/discussions`;
export const LICENSE_URL = `${REPOSITORY_URL}/blob/main/LICENSE`;
export const DISCORD_URL = "https://discord.gg/4xKsbR58bH";
export const STATUS_URL = "https://status.glyphix.site/";

// GitHub prefills the issue form from the query string, so these land the user
// on the right template with labels already applied.
export const BUG_REPORT_URL = `${ISSUES_URL}/new?labels=bug&title=${encodeURIComponent("[Bug] ")}`;
export const FEATURE_REQUEST_URL = `${ISSUES_URL}/new?labels=enhancement&title=${encodeURIComponent("[Feature] ")}`;

export const STUDIO_NAME = "BLOK. WebDev Studio";
export const STUDIO_URL = "https://blok-web-studio.netlify.app/";

// The dev channel is the working branch, not a tagged release.
export const DEV_BRANCH = "dev";
export const DEV_BRANCH_URL = `${REPOSITORY_URL}/tree/${DEV_BRANCH}`;
export const DEV_ACTIONS_URL = `${REPOSITORY_URL}/actions`;
export const DEV_BRANCH_ZIP = `${REPOSITORY_URL}/archive/refs/heads/${DEV_BRANCH}.zip`;

export const MIN_ANDROID_VERSION = "Android 12";

export type GlyphDevice = {
  name: string;
  zones: string;
};

/** Phones with a real Glyph interface, and how many mappable zones each exposes. */
export const GLYPH_DEVICES: GlyphDevice[] = [
  { name: "Nothing Phone (1)", zones: "5 zones" },
  { name: "Nothing Phone (2)", zones: "11 zones" },
  { name: "Nothing Phone (2a) / (2a) Plus", zones: "3 zones" },
  { name: "Nothing Phone (3)", zones: "11 zones" },
  { name: "Nothing Phone (3a) / (3a) Pro", zones: "3 zones" },
  { name: "Nothing Phone (4a) / (4b) / (4a) Pro", zones: "3 zones" },
];

/** Outputs that need no Glyph hardware at all. */
export const FALLBACK_OUTPUTS = [
  { name: "Haptics", detail: "Bass and beat cues drive the vibration motor." },
  { name: "Flashlight", detail: "The camera flash pulses with the track." },
];
