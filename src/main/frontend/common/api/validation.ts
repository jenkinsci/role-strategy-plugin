import { ApiError } from "./client.ts";

export interface PatternValidation {
  ok: boolean;
  message?: string;
}

/**
 * Validate a role pattern against the descriptor's {@code checkPattern}
 * endpoint, which responds with a Jenkins FormValidation HTML fragment.
 */
export async function checkPattern(
  checkPatternUrl: string,
  value: string,
): Promise<PatternValidation> {
  const body = new URLSearchParams({ value });
  const headers = crumb.wrap({
    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
  });
  const response = await fetch(checkPatternUrl, {
    method: "POST",
    headers,
    body,
  });
  if (!response.ok) {
    throw new ApiError(
      `${response.status} ${response.statusText}`,
      response.status,
      await response.text(),
    );
  }
  const html = await response.text();
  const doc = new DOMParser().parseFromString(html, "text/html");
  const error = doc.querySelector(".error");
  if (error) {
    return { ok: false, message: error.textContent?.trim() || undefined };
  }
  return { ok: true };
}

/**
 * Look a sid up via the descriptor's {@code checkSidName} endpoint and return
 * the FormValidation html snippet (icon plus resolved display name) verbatim,
 * for rendering below the name field.
 */
export async function checkSidName(
  checkSidNameUrl: string,
  value: string,
  type: string,
): Promise<string> {
  const body = new URLSearchParams({ value, type });
  const headers = crumb.wrap({
    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
  });
  const response = await fetch(checkSidNameUrl, {
    method: "POST",
    headers,
    body,
  });
  if (!response.ok) {
    throw new ApiError(
      `${response.status} ${response.statusText}`,
      response.status,
      await response.text(),
    );
  }
  return response.text();
}
