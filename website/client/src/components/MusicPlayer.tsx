import { useEffect, useId, useRef, useState, type CSSProperties, type RefObject } from "react";
import { Music2, Pause, Play, SkipBack, SkipForward, Volume1, Volume2, VolumeX } from "lucide-react";

export type PlayerTrack = {
  title: string;
  artist: string;
  source: string;
  cover: string;
};

type MusicPlayerProps = {
  track: PlayerTrack;
  isPlaying: boolean;
  volume: number;
  /** true once the hero has scrolled away and the player becomes the corner island. */
  docked: boolean;
  currentTime: number;
  duration: number;
  error: string | null;
  /** Bars are written directly by the page's animation loop, never re-rendered. */
  spectrumRef: RefObject<HTMLDivElement | null>;
  spectrumBars: number;
  onPrevious: () => void;
  onToggle: () => void;
  onNext: () => void;
  onVolume: (value: number) => void;
  onSeek: (seconds: number) => void;
};

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const whole = Math.floor(seconds);
  const minutes = Math.floor(whole / 60);
  return `${minutes}:${String(whole % 60).padStart(2, "0")}`;
}

function VolumeIcon({ volume, size }: { volume: number; size: number }) {
  if (volume <= 0.001) return <VolumeX size={size} />;
  if (volume < 0.5) return <Volume1 size={size} />;
  return <Volume2 size={size} />;
}

/**
 * Two presentations, one layout.
 *
 * The transport row (previous / play / next) is rendered in the same place in the
 * same flex container in both the hero and the docked state, so it never reflows
 * and never moves out from under a pointer mid-click. Everything that appears on
 * expansion lives in an absolutely positioned panel that only animates transform
 * and opacity, so it cannot displace the buttons or trigger layout.
 */
export default function MusicPlayer({
  track,
  isPlaying,
  volume,
  docked,
  currentTime,
  duration,
  error,
  spectrumRef,
  spectrumBars,
  onPrevious,
  onToggle,
  onNext,
  onVolume,
  onSeek,
}: MusicPlayerProps) {
  const [coverFailed, setCoverFailed] = useState(false);
  const [volumeOpen, setVolumeOpen] = useState(false);
  // Touch devices have no hover, so the artwork doubles as an explicit expand toggle.
  const [pinnedOpen, setPinnedOpen] = useState(false);
  const volumeRef = useRef<HTMLDivElement>(null);
  const panelId = useId();
  const volumeId = useId();

  useEffect(() => setCoverFailed(false), [track.cover]);

  // Collapse the docked panel whenever the player returns to the hero.
  useEffect(() => {
    if (!docked) setPinnedOpen(false);
  }, [docked]);

  useEffect(() => {
    if (!volumeOpen) return;
    const closeOnOutside = (event: PointerEvent) => {
      if (!volumeRef.current?.contains(event.target as Node)) setVolumeOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setVolumeOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [volumeOpen]);

  const progress = duration > 0 ? Math.min(1, currentTime / duration) : 0;
  const rootStyle = { "--player-progress": progress } as CSSProperties;

  const artwork = (
    <span className="player-art" aria-hidden="true">
      {coverFailed || !track.cover ? (
        <Music2 size={docked ? 15 : 18} />
      ) : (
        <img src={track.cover} alt="" onError={() => setCoverFailed(true)} />
      )}
    </span>
  );

  const seekBar = (
    <div className="player-seek">
      <span className="player-time">{formatTime(currentTime)}</span>
      <input
        type="range"
        min={0}
        max={duration > 0 ? duration : 0}
        step={0.5}
        value={Math.min(currentTime, duration || 0)}
        disabled={duration <= 0}
        aria-label={`Seek within ${track.title}`}
        onChange={(event) => onSeek(Number(event.target.value))}
      />
      <span className="player-time">{formatTime(duration)}</span>
    </div>
  );

  const spectrum = (
    <div className="player-scope">
      <span className="player-time">{formatTime(currentTime)}</span>
      <div className="player-spectrum" ref={spectrumRef} aria-hidden="true">
        {Array.from({ length: spectrumBars }, (_, index) => (
          <i key={index} />
        ))}
      </div>
      <span className="player-time">{formatTime(duration)}</span>
    </div>
  );

  const volumeControl = (
    <div className={`player-volume ${volumeOpen ? "is-open" : ""}`} ref={volumeRef}>
      <button
        type="button"
        className="player-volume-trigger"
        aria-label={volumeOpen ? "Close volume control" : "Open volume control"}
        aria-expanded={volumeOpen}
        aria-controls={volumeId}
        onClick={() => setVolumeOpen((open) => !open)}
      >
        <VolumeIcon volume={volume} size={15} />
      </button>
      <div className="player-volume-popover" id={volumeId} hidden={!volumeOpen}>
        <input
          type="range"
          min={0}
          max={1}
          step={0.02}
          value={volume}
          aria-label="Player volume"
          onChange={(event) => onVolume(Number(event.target.value))}
        />
      </div>
    </div>
  );

  return (
    <section
      className={`player ${docked ? "is-docked" : "is-inline"} ${pinnedOpen ? "is-pinned" : ""}`}
      style={rootStyle}
      aria-label="Glyphix demo player"
    >
      {docked ? (
        <div className="player-panel" id={panelId}>
          <p className="player-panel-title">
            <strong>{track.title}</strong>
            <span>{track.artist}</span>
          </p>
          {seekBar}
        </div>
      ) : null}

      <div className="player-bar">
        {docked ? (
          <button
            type="button"
            className="player-art-button"
            aria-label={pinnedOpen ? "Hide track details" : "Show track details"}
            aria-expanded={pinnedOpen}
            aria-controls={panelId}
            onClick={() => setPinnedOpen((open) => !open)}
          >
            {artwork}
          </button>
        ) : (
          artwork
        )}

        {!docked ? (
          <p className="player-meta">
            <strong>{track.title}</strong>
            <span>{track.artist}</span>
          </p>
        ) : null}

        <div className="player-transport">
          <button type="button" onClick={onPrevious} aria-label="Previous track">
            <SkipBack size={15} fill="currentColor" />
          </button>
          <button
            type="button"
            className="player-play"
            onClick={onToggle}
            aria-label={isPlaying ? `Pause ${track.title}` : `Play ${track.title}`}
          >
            {isPlaying ? <Pause size={16} fill="currentColor" /> : <Play size={16} fill="currentColor" />}
          </button>
          <button type="button" onClick={onNext} aria-label="Next track">
            <SkipForward size={15} fill="currentColor" />
          </button>
        </div>

        {!docked ? spectrum : null}
        {volumeControl}

        <span className="player-progress-rail" aria-hidden="true">
          <i />
        </span>
      </div>

      {error ? (
        <p className="player-error" role="status">
          {error}
        </p>
      ) : null}
    </section>
  );
}
