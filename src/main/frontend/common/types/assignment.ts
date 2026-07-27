/** How a sid entry is scoped. EITHER is a legacy ambiguous entry that applies
    to both a user and a group of the same name. */
export type SidType = "USER" | "GROUP" | "EITHER";

/** One user/group row of the Assign Roles page: a sid plus the names of the
    roles it is assigned to within one role type. */
export interface SidEntry {
  name: string;
  type: SidType;
  roles: string[];
}

/** Result of resolving a sid against the security realm. `internal` covers the
    reserved `anonymous` user and `authenticated` group; `unknown` means the
    realm cannot decide or the caller may not probe it. */
export type SidResolutionState = "found" | "not-found" | "unknown" | "internal";

export interface SidInfo {
  sid: string;
  type: SidType;
  resolution: SidResolutionState;
  /** Set when resolution is found or internal: what the sid resolved to. */
  foundAs?: "USER" | "GROUP";
  /** Only set when the resolved name differs from the sid. */
  displayName?: string;
}
