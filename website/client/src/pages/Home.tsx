import { memo, useEffect, useRef, useState, type CSSProperties, type RefObject } from "react";
import { ArrowDownRight, Github, Volume2 } from "lucide-react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import Lenis from "lenis";
import TopDock from "@/components/TopDock";
import SiteFooter from "@/components/SiteFooter";
import MusicPlayer, { type PlayerTrack } from "@/components/MusicPlayer";
import { RELEASES_URL, REPOSITORY_URL } from "@/lib/site";

const MATRIX_COLUMNS = 60;
const MATRIX_ROWS = 42;
const matrixCells = Array.from({ length: MATRIX_COLUMNS * MATRIX_ROWS }, (_, index) => ({ column: index % MATRIX_COLUMNS, row: Math.floor(index / MATRIX_COLUMNS), id: index }));

/** Bars drawn in the player. Written imperatively, never through React state. */
const SPECTRUM_BARS = 28;
/** Seconds of overlap when one track hands over to the next. */
const CROSSFADE_SECONDS = 3;
/** Volume changes ramp over this long so muting fades instead of cutting. */
const VOLUME_RAMP_SECONDS = 0.4;

const tracks: PlayerTrack[] = [
  { title: "House (1)", artist: "Glyphix", source: "/audio/glyphix.m4a", cover: "" },
  { title: "House (2)", artist: "Glyphix", source: "/audio/track-01.m4a", cover: "" },
  { title: "House (3)", artist: "Glyphix", source: "/audio/track-03.m4a", cover: "" },
];

const supportedDevices = ["Nothing Phone (1)", "Nothing Phone (2), (2a), (2a) Plus", "Nothing Phone (3), (3a), (3a) Pro", "Nothing Phone (4a), (4b), (4a) Pro"];
const zones = [["01", "Bass", "28–72 Hz"], ["02", "Kick", "72–120 Hz"], ["03", "Low", "120–360 Hz"], ["04", "Mid", "360–2k Hz"], ["05", "High", "2–7k Hz"], ["06", "Air", "7–16k Hz"]];
type AudioBands = { bass: number; low: number; mid: number; high: number; overall: number };
const SILENT_BANDS: AudioBands = { bass: 0, low: 0, mid: 0, high: 0, overall: 0 };

type DeckId = "a" | "b";
const otherDeck = (deck: DeckId): DeckId => (deck === "a" ? "b" : "a");

function getMatrixLight(cell: { column: number; row: number }, bands: AudioBands) {
  if (bands.overall < .025) return { intensity: 0, lime: false };
  let intensity = 0;
  const waveY = 22 + Math.sin(cell.column * .22 + bands.high * 14) * (2 + bands.mid * 9) + (bands.low - .5) * 6;
  const waveDistance = Math.abs(cell.row - waveY);
  if (waveDistance < .55 && bands.mid > .06) intensity = 3;
  else if (waveDistance < 1.3 && bands.mid > .035) intensity = 2;
  else if (waveDistance < 2.05 && bands.overall > .05) intensity = 1;

  [11, 25, 39, 53].forEach((center, index) => {
    const levels = [bands.bass, bands.low, bands.mid, bands.high];
    const height = Math.round(levels[index] * 18);
    const horizontal = Math.abs(cell.column - center);
    const vertical = Math.abs(cell.row - 37);
    if (horizontal < 1.25 && vertical < height) intensity = Math.max(intensity, vertical < height * .3 ? 3 : vertical < height * .67 ? 2 : 1);
  });

  const ring = Math.abs(Math.hypot(cell.column - 47, cell.row - 15) - (3 + bands.bass * 15));
  if (ring < .5 && bands.bass > .07) intensity = Math.max(intensity, 3);
  else if (ring < 1.15 && bands.bass > .04) intensity = Math.max(intensity, 1);
  return { intensity, lime: intensity === 3 && (bands.bass > .24 || (bands.high > .35 && cell.column % 3 === 0)) };
}

const MatrixField = memo(function MatrixField({ fieldRef, gridRef }: { fieldRef: RefObject<HTMLDivElement | null>; gridRef: RefObject<HTMLDivElement | null> }) {
  return <div className="matrix-field" ref={fieldRef} aria-hidden="true"><div className="matrix-grid" ref={gridRef}>{matrixCells.map((cell) => <i key={cell.id} className="matrix-cell" />)}</div></div>;
});

export default function Home() {
  const [activeTrack, setActiveTrack] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [volume, setVolume] = useState(0.72);
  const [bands, setBands] = useState<AudioBands>(SILENT_BANDS);
  const mainRef = useRef<HTMLElement>(null);
  const cameraRef = useRef<HTMLDivElement>(null);
  const matrixFieldRef = useRef<HTMLDivElement>(null);
  const matrixGridRef = useRef<HTMLDivElement>(null);
  const spectrumRef = useRef<HTMLDivElement>(null);
  const finaleRef = useRef<HTMLDivElement>(null);

  // Two decks so a track can fade out while the next fades in. One <audio> element
  // cannot overlap itself, so a single-element player can only ever hard-cut.
  const deckARef = useRef<HTMLAudioElement>(null);
  const deckBRef = useRef<HTMLAudioElement>(null);
  const activeDeckRef = useRef<DeckId>("a");
  const gainARef = useRef<GainNode | null>(null);
  const gainBRef = useRef<GainNode | null>(null);
  const masterGainRef = useRef<GainNode | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const analysisDataRef = useRef<Uint8Array<ArrayBuffer> | null>(null);
  const crossfadingRef = useRef(false);

  const animationRef = useRef(0);
  const lastBandCommitRef = useRef(0);
  const lastUiCommitRef = useRef(0);
  const spinRef = useRef(0);
  const liveBandsRef = useRef<AudioBands>(SILENT_BANDS);
  const matrixStatesRef = useRef<Int8Array | null>(null);
  const autoplayAttemptedRef = useRef(false);
  const zoneCardRef = useRef<HTMLDivElement>(null);
  const heroRef = useRef<HTMLElement>(null);
  const [zonesVisible, setZonesVisible] = useState(false);
  const [playerFloating, setPlayerFloating] = useState(false);
  const [playerError, setPlayerError] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  const deckElement = (deck: DeckId) => (deck === "a" ? deckARef.current : deckBRef.current);
  const deckGain = (deck: DeckId) => (deck === "a" ? gainARef.current : gainBRef.current);

  const paintMatrix = (nextBands: AudioBands, active: boolean) => {
    const grid = matrixGridRef.current;
    const field = matrixFieldRef.current;
    if (!grid || !field) return;
    const states = matrixStatesRef.current ?? new Int8Array(matrixCells.length);
    matrixStatesRef.current = states;
    field.classList.toggle("music-playing", active && nextBands.overall >= .025);
    matrixCells.forEach((cell) => {
      const dot = grid.children[cell.id] as HTMLElement | undefined;
      if (!dot) return;
      const { intensity, lime } = active ? getMatrixLight(cell, nextBands) : { intensity: 0, lime: false };
      const state = intensity + (lime ? 4 : 0);
      if (states[cell.id] === state) return;
      states[cell.id] = state;
      dot.classList.toggle("music-lit", intensity > 0);
      dot.classList.toggle("music-lime", lime);
      if (intensity > 0) dot.dataset.intensity = String(intensity);
      else delete dot.dataset.intensity;
    });
  };

  /** Player spectrum + finale rings are painted straight to the DOM, off the React path. */
  const paintDecoration = (values: Uint8Array | null, nextBands: AudioBands, active: boolean) => {
    const spectrum = spectrumRef.current;
    if (spectrum) {
      const bars = spectrum.children;
      for (let index = 0; index < bars.length; index += 1) {
        let level = 0;
        if (active && values) {
          // Weight the low bins wider so the bars read musically rather than bunching up.
          const from = Math.floor(1 + Math.pow(index / SPECTRUM_BARS, 1.7) * 108);
          const to = Math.max(from + 1, Math.floor(1 + Math.pow((index + 1) / SPECTRUM_BARS, 1.7) * 108));
          let total = 0;
          for (let bin = from; bin < to; bin += 1) total += values[bin] || 0;
          level = Math.min(1, (total / (to - from) / 255) * 1.5);
        }
        (bars[index] as HTMLElement).style.setProperty("--level", level.toFixed(3));
      }
    }

    const finale = finaleRef.current;
    if (finale) {
      // Kept deliberately gentle: a few percent of scale and a slow drift.
      spinRef.current = (spinRef.current + (active ? 0.12 + nextBands.overall * 0.9 : 0.03)) % 360;
      finale.style.setProperty("--finale-spin", spinRef.current.toFixed(2));
      finale.style.setProperty("--finale-pulse", (active ? nextBands.bass : 0).toFixed(3));
      finale.style.setProperty("--finale-energy", (active ? nextBands.overall : 0).toFixed(3));
    }
  };

  // Built once on mount. An AudioContext created without a gesture starts suspended,
  // which is fine - it is resumed on the first play. Each deck gets its own gain for
  // the crossfade, and both feed a master gain so volume can be ramped rather than cut.
  const setupAudioGraph = () => {
    if (analyserRef.current) return true;
    const deckA = deckARef.current;
    const deckB = deckBRef.current;
    if (!deckA || !deckB) return false;
    try {
      const context = new AudioContext();
      const analyser = context.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = .68;

      const master = context.createGain();
      master.gain.value = volume;

      const gainA = context.createGain();
      const gainB = context.createGain();
      gainA.gain.value = 1;
      gainB.gain.value = 0;

      // Level is the master gain's job from here on. Leaving an element at the
      // pre-graph fallback volume would attenuate that deck twice and make the two
      // decks play at different levels through a crossfade.
      deckA.volume = 1;
      deckB.volume = 1;

      context.createMediaElementSource(deckA).connect(gainA);
      context.createMediaElementSource(deckB).connect(gainB);
      gainA.connect(master);
      gainB.connect(master);
      master.connect(analyser);
      analyser.connect(context.destination);

      audioContextRef.current = context;
      analyserRef.current = analyser;
      masterGainRef.current = master;
      gainARef.current = gainA;
      gainBRef.current = gainB;
      analysisDataRef.current = new Uint8Array(analyser.frequencyBinCount);
      return true;
    } catch {
      return false;
    }
  };

  const stopAudioAnalysis = () => {
    if (animationRef.current) cancelAnimationFrame(animationRef.current);
    animationRef.current = 0;
    liveBandsRef.current = SILENT_BANDS;
    paintMatrix(SILENT_BANDS, false);
    paintDecoration(null, SILENT_BANDS, false);
    setBands(SILENT_BANDS);
  };

  const startAudioAnalysis = () => {
    const analyser = analyserRef.current;
    const values = analysisDataRef.current;
    if (!analyser || !values || animationRef.current) return;
    const average = (from: number, to: number) => {
      let total = 0;
      for (let index = from; index < to; index += 1) total += values[index] || 0;
      return total / Math.max(1, to - from) / 255;
    };
    const update = (timestamp: number) => {
      if (timestamp - lastBandCommitRef.current > 40) {
        lastBandCommitRef.current = timestamp;
        analyser.getByteFrequencyData(values);
        const previous = liveBandsRef.current;
        const target = { bass: average(1, 7), low: average(7, 19), mid: average(19, 54), high: average(54, 108), overall: average(1, 108) };
        const smooth = (current: number, next: number) => current * .48 + next * .52;
        const next = { bass: smooth(previous.bass, target.bass), low: smooth(previous.low, target.low), mid: smooth(previous.mid, target.mid), high: smooth(previous.high, target.high), overall: smooth(previous.overall, target.overall) };
        liveBandsRef.current = next;
        paintMatrix(next, true);
        paintDecoration(values, next, true);
        if (timestamp - lastUiCommitRef.current > 96 && Math.max(Math.abs(next.bass - previous.bass), Math.abs(next.low - previous.low), Math.abs(next.mid - previous.mid), Math.abs(next.high - previous.high), Math.abs(next.overall - previous.overall)) >= .012) {
          lastUiCommitRef.current = timestamp;
          setBands(next);
        }
      }
      animationRef.current = requestAnimationFrame(update);
    };
    update(performance.now());
  };

  const beginPlayback = () => {
    setPlayerError(null);
    setIsPlaying(true);
    if (setupAudioGraph()) {
      audioContextRef.current?.resume().catch(() => undefined);
      startAudioAnalysis();
    }
  };

  /**
   * Hand over from the active deck to the other one, overlapping both for
   * CROSSFADE_SECONDS. Used for track ends and for the next/previous buttons, so
   * every transition sounds the same.
   */
  const crossfadeTo = (index: number) => {
    if (crossfadingRef.current) return;
    if (!setupAudioGraph()) return;
    const context = audioContextRef.current;
    const from = activeDeckRef.current;
    const to = otherDeck(from);
    const fromGain = deckGain(from);
    const toGain = deckGain(to);
    const toElement = deckElement(to);
    const fromElement = deckElement(from);
    if (!context || !fromGain || !toGain || !toElement || !fromElement) return;

    crossfadingRef.current = true;
    context.resume().catch(() => undefined);

    toElement.src = tracks[index].source;
    toElement.currentTime = 0;
    toElement.load();

    const now = context.currentTime;
    fromGain.gain.cancelScheduledValues(now);
    toGain.gain.cancelScheduledValues(now);
    fromGain.gain.setValueAtTime(fromGain.gain.value, now);
    toGain.gain.setValueAtTime(0, now);
    fromGain.gain.linearRampToValueAtTime(0, now + CROSSFADE_SECONDS);
    toGain.gain.linearRampToValueAtTime(1, now + CROSSFADE_SECONDS);

    activeDeckRef.current = to;
    setActiveTrack(index);
    setCurrentTime(0);
    setDuration(0);

    toElement
      .play()
      .then(() => beginPlayback())
      .catch(() => {
        // Autoplay refusal: undo the handover so the visible state stays truthful.
        crossfadingRef.current = false;
        activeDeckRef.current = from;
        fromGain.gain.cancelScheduledValues(context.currentTime);
        fromGain.gain.setValueAtTime(1, context.currentTime);
        toGain.gain.setValueAtTime(0, context.currentTime);
        setIsPlaying(false);
      });

    window.setTimeout(() => {
      if (activeDeckRef.current !== from) fromElement.pause();
      crossfadingRef.current = false;
    }, CROSSFADE_SECONDS * 1000);
  };

  const selectTrack = (direction: number) => {
    crossfadeTo((activeTrack + direction + tracks.length) % tracks.length);
  };

  const handleAudioError = () => {
    setPlayerError("Audio failed to load. Check your connection, or try again later.");
    setIsPlaying(false);
    stopAudioAnalysis();
  };

  const togglePlayback = () => {
    const element = deckElement(activeDeckRef.current);
    if (!element) return;
    if (isPlaying) {
      element.pause();
      setIsPlaying(false);
      stopAudioAnalysis();
    } else {
      element.play().then(() => beginPlayback()).catch(() => setIsPlaying(false));
    }
  };

  const seekTo = (seconds: number) => {
    const element = deckElement(activeDeckRef.current);
    if (!element || !Number.isFinite(seconds)) return;
    element.currentTime = seconds;
    setCurrentTime(seconds);
  };

  // Volume is ramped on the master gain, so dragging to zero fades out instead of
  // cutting. The element volumes stay at 1 - the graph owns level from here on.
  useEffect(() => {
    const master = masterGainRef.current;
    const context = audioContextRef.current;
    if (master && context) {
      const now = context.currentTime;
      master.gain.cancelScheduledValues(now);
      master.gain.setValueAtTime(master.gain.value, now);
      master.gain.linearRampToValueAtTime(Math.max(0.0001, volume), now + VOLUME_RAMP_SECONDS);
      return;
    }
    const element = deckElement(activeDeckRef.current);
    if (element) element.volume = volume;
  }, [volume]);

  // Autoplay on arrival. Browsers that refuse leave the player ready for a manual start.
  useEffect(() => {
    if (autoplayAttemptedRef.current) return;
    autoplayAttemptedRef.current = true;
    const element = deckARef.current;
    if (!element) return;
    element.play().then(() => beginPlayback()).catch(() => undefined);
  }, []);

  // A refused autoplay is retried on the first real interaction anywhere on the page.
  useEffect(() => {
    if (isPlaying) return;
    const tryStart = () => {
      const element = deckElement(activeDeckRef.current);
      element?.play().then(() => beginPlayback()).catch(() => undefined);
    };
    window.addEventListener("pointerdown", tryStart, { once: true });
    window.addEventListener("keydown", tryStart, { once: true });
    return () => {
      window.removeEventListener("pointerdown", tryStart);
      window.removeEventListener("keydown", tryStart);
    };
  }, [isPlaying]);

  useEffect(() => {
    paintMatrix(liveBandsRef.current, isPlaying);
  }, [isPlaying]);

  useEffect(() => () => { stopAudioAnalysis(); audioContextRef.current?.close().catch(() => undefined); }, []);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    gsap.registerPlugin(ScrollTrigger);
    const lenis = new Lenis({ lerp: 0.1, smoothWheel: true, wheelMultiplier: 0.82, autoRaf: false });
    const update = (time: number) => lenis.raf(time * 1000);
    lenis.on("scroll", ScrollTrigger.update);
    gsap.ticker.add(update);
    gsap.ticker.lagSmoothing(0);
    const context = gsap.context(() => {
      if (cameraRef.current) gsap.fromTo(cameraRef.current, { xPercent: -50, yPercent: -50, x: -120, y: 80, scale: 1.04, rotation: -5 }, { x: 130, y: -100, scale: 1.28, rotation: 5, ease: "none", scrollTrigger: { trigger: mainRef.current, start: "top top", end: "bottom bottom", scrub: 1.15 } });
      gsap.utils.toArray<HTMLElement>(".gsap-reveal").forEach((element) => gsap.fromTo(element, { autoAlpha: 0, y: 34 }, { autoAlpha: 1, y: 0, duration: 1, ease: "power3.out", scrollTrigger: { trigger: element, start: "top 86%", once: true } }));
      gsap.utils.toArray<HTMLElement>(".matrix-scene").forEach((scene) => ScrollTrigger.create({ trigger: scene, start: "top 88%", end: "bottom 12%", toggleClass: { targets: scene, className: "is-motion-active" } }));
      if (zoneCardRef.current) ScrollTrigger.create({ trigger: zoneCardRef.current, start: "top 78%", once: true, onEnter: () => setZonesVisible(true) });
      if (heroRef.current) ScrollTrigger.create({ trigger: heroRef.current, start: "bottom 64%", onEnter: () => setPlayerFloating(true), onEnterBack: () => setPlayerFloating(false), onLeaveBack: () => setPlayerFloating(false) });
    }, mainRef);
    return () => { context.revert(); lenis.destroy(); gsap.ticker.remove(update); };
  }, []);

  const handleTimeUpdate = (deck: DeckId) => (event: React.SyntheticEvent<HTMLAudioElement>) => {
    if (activeDeckRef.current !== deck) return;
    const element = event.currentTarget;
    setCurrentTime(element.currentTime);
    // Start the handover early enough that the two tracks actually overlap.
    if (
      !crossfadingRef.current &&
      Number.isFinite(element.duration) &&
      element.duration > 0 &&
      element.duration - element.currentTime <= CROSSFADE_SECONDS
    ) {
      crossfadeTo((activeTrack + 1) % tracks.length);
    }
  };

  const responseActive = isPlaying && volume > .01;
  const responseStyle = { "--bass": bands.bass.toFixed(3), "--pulse-duration": `${Math.max(.52, 1.7 - bands.bass * 1.2).toFixed(2)}s` } as CSSProperties;

  return <div className="matrix-site">
    <TopDock repositoryUrl={REPOSITORY_URL} />
    <main id="top" ref={mainRef}><div className="matrix-viewport"><div className="matrix-camera" ref={cameraRef}><MatrixField fieldRef={matrixFieldRef} gridRef={matrixGridRef} /></div></div>
      <section className="matrix-scene hero-scene" ref={heroRef} aria-labelledby="hero-title"><div className="hero-content gsap-reveal"><h1 id="hero-title">music in<br /><strong>motion.</strong></h1><p>Glyphix reads whatever is playing and drives the Glyph LEDs in real time. Map frequency bands to individual zones, or use haptics and the flashlight on any Android phone.</p><div className="hero-actions"><a className="action-button action-primary" href={RELEASES_URL} target="_blank" rel="noreferrer">Download APK <ArrowDownRight size={17} /></a><button className="action-button action-quiet" onClick={() => document.getElementById("mapping")?.scrollIntoView({ behavior: "smooth", block: "start" })}>How it works <span /></button></div></div><MusicPlayer track={tracks[activeTrack]} isPlaying={isPlaying} volume={volume} docked={playerFloating} currentTime={currentTime} duration={duration} error={playerError} spectrumRef={spectrumRef} spectrumBars={SPECTRUM_BARS} onPrevious={() => selectTrack(-1)} onToggle={togglePlayback} onNext={() => selectTrack(1)} onVolume={setVolume} onSeek={seekTo} /></section>
      <section className="matrix-scene mapping-scene" id="mapping" aria-labelledby="mapping-title"><div className="mapping-copy gsap-reveal"><h2 id="mapping-title">Each <em>light</em>,<br />gets a <em>range.</em></h2><p>Assign low, mid, or high frequencies to a Glyph zone. Each zone responds separately, so the lights follow more of the song.</p></div><div className={`zone-card gsap-reveal ${zonesVisible ? "is-visible" : ""}`} ref={zoneCardRef} aria-label="Glyphix zone mapping overview"><div className="zone-card-head"><span>Zones</span><b>6 active</b></div>{zones.map(([number, label, range], index) => <div className="zone-line" key={number} style={{ "--meter": `${51 + (index * 7) % 42}%`, "--delay": `${index * 80}ms` } as CSSProperties}><span>{number}</span><strong>{label}</strong><i><b /></i><em>{range}</em></div>)}</div></section>
      <section className="matrix-scene brightness-scene" id="brightness" aria-labelledby="brightness-title"><div className="brightness-copy gsap-reveal"><h2 id="brightness-title">More than<br />on or <em>off.</em></h2><p>Glyphix uses 4,096 brightness levels instead of a few preset steps. Small changes in a track can show up as small changes in light.</p><a className="action-button action-quiet" href={REPOSITORY_URL} target="_blank" rel="noreferrer">View source <ArrowDownRight size={16} /></a></div><div className="brightness-minimal gsap-reveal"><div className="brightness-minimal-value"><span>12-BIT LIGHT OUTPUT</span><strong>4,096</strong><small>distinct levels of brightness</small></div><div className="brightness-minimal-bar" aria-hidden="true"><i /></div><div className="brightness-minimal-facts"><span><b>60</b> frames per second</span><span><b>20<span>ms</span></b> analysis window</span></div></div></section>
      <section className="matrix-scene response-scene" id="response" aria-labelledby="response-title"><div className={`response-graphic gsap-reveal ${responseActive ? "is-audio-active" : "is-audio-idle"}`} style={responseStyle}><div className="response-ring ring-a" /><div className="response-ring ring-b" /><div className="response-ring ring-c" /><div className="response-core"><Volume2 size={34} /></div></div><div className="response-copy gsap-reveal"><h2 id="response-title">More than<br /><em>nothing.</em></h2><p>Don't have a Nothing Phone? No worries, use flashlight and haptics modes on any android phone out there!</p><div className="response-notes"><span><i /> Vibration</span><span><i /> Flashlight</span><span><i /> Beat pulses</span></div></div></section>
      <section className="matrix-scene devices-scene" id="devices" aria-labelledby="devices-title"><div className="devices-title gsap-reveal"><h2 id="devices-title">Supported<br /><em>phones.</em></h2><p>Glyph support is available across the models below. Haptics and flashlight mode are available on Android phones more broadly.</p></div><div className="device-groups gsap-reveal"><div className="device-list"><div className="device-list-head"><span>Full Glyph support</span></div>{supportedDevices.map((device, index) => <div className="device-line" key={device}><span>0{index + 1}</span><strong>{device}</strong><i /></div>)}</div><div className="device-list"><div className="device-list-head"><span>Haptics &amp; Flash Support</span></div><div className="device-line"><span>01</span><strong>Any Android device</strong><i /></div></div></div></section>
      <section className="matrix-scene finale-scene" aria-labelledby="finale-title">
        <div className="finale-matrix" ref={finaleRef} aria-hidden="true">
          <i className="finale-ring finale-ring-1" />
          <i className="finale-ring finale-ring-2" />
          <i className="finale-ring finale-ring-3" />
          <i className="finale-ring finale-ring-4" />
          <i className="finale-orbit finale-orbit-a"><b /></i>
          <i className="finale-orbit finale-orbit-b"><b /></i>
        </div>
        <div className="finale-content gsap-reveal"><h2 id="finale-title">Play a track.<br /><em>Watch the lights.</em></h2><p>Download Glyphix and try it with your own music.</p><div className="finale-actions"><a className="action-button action-primary" href={RELEASES_URL} target="_blank" rel="noreferrer">Download APK <ArrowDownRight size={17} /></a><a className="action-button action-quiet" href={REPOSITORY_URL} target="_blank" rel="noreferrer">GitHub <Github size={16} /></a></div></div>
      </section>
      <audio ref={deckARef} src={tracks[0].source} preload="auto" onPlay={beginPlayback} onError={handleAudioError} onTimeUpdate={handleTimeUpdate("a")} onLoadedMetadata={(event) => { if (activeDeckRef.current === "a") setDuration(Number.isFinite(event.currentTarget.duration) ? event.currentTarget.duration : 0); }} />
      <audio ref={deckBRef} preload="auto" onPlay={beginPlayback} onError={handleAudioError} onTimeUpdate={handleTimeUpdate("b")} onLoadedMetadata={(event) => { if (activeDeckRef.current === "b") setDuration(Number.isFinite(event.currentTarget.duration) ? event.currentTarget.duration : 0); }} />
    </main>
    <SiteFooter />
  </div>;
}
