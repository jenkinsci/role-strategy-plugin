import { useEffect, useState } from "react";

import type { StrategyClient } from "../common/api/strategy.ts";
import { Dialog } from "../common/components/Dialog.tsx";

// Search limits carried over from the previous manage page.
const MAX_ITEMS = 15;
const MAX_AGENTS = 10;

interface MatchingDialogProps {
  kind: "items" | "agents";
  pattern: string;
  client: StrategyClient;
  onClose: () => void;
}

/** Lists the jobs or agents whose names match a role's pattern. */
export function MatchingDialog({
  kind,
  pattern,
  client,
  onClose,
}: MatchingDialogProps) {
  const [names, setNames] = useState<string[] | null>(null);
  const [totalCount, setTotalCount] = useState(0);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        if (kind === "items") {
          const result = await client.getMatchingJobs(pattern, MAX_ITEMS);
          if (cancelled) return;
          setNames(result.matchingJobs);
          setTotalCount(result.itemCount);
        } else {
          const result = await client.getMatchingAgents(pattern, MAX_AGENTS);
          if (cancelled) return;
          setNames(result.matchingAgents);
          setTotalCount(result.agentCount);
        }
      } catch (err) {
        console.error("Failed to fetch matches for pattern", pattern, err);
        if (!cancelled) setFailed(true);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [kind, pattern, client]);

  const noun = kind === "items" ? "items" : "agents";
  const max = kind === "items" ? MAX_ITEMS : MAX_AGENTS;

  let title = `Matching ${noun}`;
  if (names !== null) {
    if (names.length === 0) {
      title = `No ${noun} found matching "${pattern}"`;
    } else if (totalCount > names.length) {
      title = `First ${max} ${noun} (out of ${totalCount}) matching "${pattern}"`;
    } else {
      title = `${kind === "items" ? "Items" : "Agents"} matching "${pattern}"`;
    }
  }

  return (
    <Dialog title={title} onClose={onClose}>
      {failed ? (
        <div className="jenkins-alert jenkins-alert-danger">
          Unable to fetch matching {noun}.
        </div>
      ) : names === null ? (
        <div className="jenkins-spinner">Loading…</div>
      ) : (
        names.length > 0 && (
          <ul className="rsp-matching-list">
            {names.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        )
      )}
    </Dialog>
  );
}
