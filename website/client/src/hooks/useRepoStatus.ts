import { useEffect, useState } from "react";

// A status indicator is only worth showing if it is actually measuring something.
// This probes the real endpoint and reports what came back, replacing the hardcoded
// "Active" pills that the previous Support page displayed unconditionally.

export type ServiceStatus =
  | { state: "checking" }
  | { state: "operational"; checkedAt: Date }
  | { state: "unreachable"; checkedAt: Date };

/**
 * Reachability probe for a service on another origin.
 *
 * A cross-origin status page will not send CORS headers, so the response body is
 * unreadable either way. `mode: "no-cors"` still distinguishes the case that matters:
 * an opaque response means something answered, a rejection means nothing did. That is
 * a reachability check, not a health check, and the UI wording must not overclaim.
 */
export function useServiceStatus(url: string, timeoutMs = 6000): ServiceStatus {
  const [status, setStatus] = useState<ServiceStatus>({ state: "checking" });

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), timeoutMs);

    fetch(url, { signal: controller.signal, mode: "no-cors", cache: "no-store" })
      .then(() => setStatus({ state: "operational", checkedAt: new Date() }))
      .catch(() => setStatus({ state: "unreachable", checkedAt: new Date() }))
      .finally(() => window.clearTimeout(timer));

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [url, timeoutMs]);

  return status;
}
