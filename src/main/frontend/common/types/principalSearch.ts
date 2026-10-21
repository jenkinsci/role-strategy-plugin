import type { DetailedHTMLProps, HTMLAttributes } from "react";

/** A user or group returned by the auth-commons principal search endpoint. */
export interface SearchedPrincipal {
  sid: string;
  type: "USER" | "GROUP";
  displayName: string;
  description?: string | null;
  avatarUrl?: string | null;
}

/**
 * The `<auth-commons-principal-search>` custom element, provided by the
 * auth-commons plugin as a Stapler adjunct loaded by the page.
 */
export interface PrincipalSearchElement extends HTMLElement {
  value: string;
  readonly selectedPrincipal: SearchedPrincipal | null;
  readonly selectedPrincipals: SearchedPrincipal[];
  readonly pendingText: string;
  /** Principals for which this returns true are not offered. */
  exclude?: (principal: SearchedPrincipal) => boolean;
  clear(): void;
}

export type PrincipalSelectedEvent = CustomEvent<SearchedPrincipal>;

declare module "react" {
  namespace JSX {
    interface IntrinsicElements {
      "auth-commons-principal-search": DetailedHTMLProps<
        HTMLAttributes<PrincipalSearchElement>,
        PrincipalSearchElement
      > & {
        "search-url"?: string;
        type?: string;
        multiple?: boolean;
        name?: string;
        value?: string;
        placeholder?: string;
      };
    }
  }
}
