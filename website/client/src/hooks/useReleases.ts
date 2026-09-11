import { useEffect, useState } from "react";
import { REPO_NAME, REPO_OWNER } from "@/lib/site";

// The Download page reports real build metadata or it reports nothing.
// Everything below is read live from the public GitHub Releases API — there are
// no hardcoded version numbers, file sizes or download counts anywhere on this site.

const RELEASES_ENDPOINT = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases`;

export type ReleaseAsset = {
  id: number;
  name: string;
  /** Human-readable size, e.g. "18.4 MB". */
  size: string;
  /** Best-guess ABI parsed from the filename, e.g. "arm64-v8a". */
  architecture: string;
  downloadUrl: string;
};

export type Release = {
  id: number;
  version: string;
  title: string;
  publishedAt: string;
  isPrerelease: boolean;
  htmlUrl: string;
  assets: ReleaseAsset[];
};

export type ReleasesState =
  /** Request in flight. */
  | { status: "loading" }
  /** The repository is reachable and has published at least one release. */
  | { status: "ready"; latest: Release; archive: Release[] }
  /** The repository is reachable but has published nothing yet. */
  | { status: "empty" }
  /** Network failure, rate limit, or the repository is gone. */
  | { status: "error"; message: string };

const ABI_PATTERNS: Array<[RegExp, string]> = [
  [/arm64[-_]?v8a|aarch64/i, "arm64-v8a"],
  [/armeabi[-_]?v7a|armv7/i, "armeabi-v7a"],
  [/x86[-_]?64/i, "x86_64"],
  [/x86/i, "x86"],
  [/universal|all[-_]?abi/i, "Universal"],
];

function readArchitecture(assetName: string): string {
  for (const [pattern, label] of ABI_PATTERNS) {
    if (pattern.test(assetName)) return label;
  }
  return "Universal";
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "Unknown size";
  const units = ["B", "KB", "MB", "GB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 100 || unit === 0 ? 0 : 1)} ${units[unit]}`;
}

/** Installable Android artifacts only — ignore source tarballs and checksums. */
function isInstallable(assetName: string): boolean {
  return /\.(apk|aab)$/i.test(assetName);
}

type GithubAsset = { id: number; name: string; size: number; browser_download_url: string };
type GithubRelease = {
  id: number;
  tag_name: string;
  name: string | null;
  draft: boolean;
  prerelease: boolean;
  published_at: string | null;
  html_url: string;
  assets: GithubAsset[];
};

function toRelease(raw: GithubRelease): Release {
  const installable = raw.assets.filter((asset) => isInstallable(asset.name));
  return {
    id: raw.id,
    version: raw.tag_name,
    title: raw.name?.trim() || raw.tag_name,
    publishedAt: raw.published_at ?? "",
    isPrerelease: raw.prerelease,
    htmlUrl: raw.html_url,
    assets: installable.map((asset) => ({
      id: asset.id,
      name: asset.name,
      size: formatBytes(asset.size),
      architecture: readArchitecture(asset.name),
      downloadUrl: asset.browser_download_url,
    })),
  };
}

export function useReleases(): ReleasesState {
  const [state, setState] = useState<ReleasesState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();

    fetch(RELEASES_ENDPOINT, {
      signal: controller.signal,
      headers: { Accept: "application/vnd.github+json" },
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(
            response.status === 403 || response.status === 429
              ? "GitHub rate limit reached from this network. Open the releases page directly."
              : `GitHub returned ${response.status}.`,
          );
        }
        const payload: GithubRelease[] = await response.json();
        const published = payload.filter((entry) => !entry.draft).map(toRelease);

        if (published.length === 0) {
          setState({ status: "empty" });
          return;
        }
        setState({ status: "ready", latest: published[0], archive: published.slice(1) });
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setState({
          status: "error",
          message: error instanceof Error ? error.message : "Could not reach GitHub.",
        });
      });

    return () => controller.abort();
  }, []);

  return state;
}

export function formatReleaseDate(iso: string): string {
  if (!iso) return "Unknown date";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "Unknown date";
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
}
