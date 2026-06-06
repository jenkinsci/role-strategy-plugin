declare module "*.scss";
declare module "*.css";

/**
 * Jenkins core's CSRF crumb helper (defined in hudson-behavior.js, loaded on
 * every Jenkins page). `wrap` adds the crumb header to the given headers.
 */
declare const crumb: {
  wrap(headers: Record<string, string>): Record<string, string>;
};

/**
 * Jenkins core's dialog helper (defined in dialogs.js, loaded on every
 * Jenkins page). `confirm` resolves `true` when the user confirms and
 * rejects when the dialog is cancelled.
 */
declare const dialog: {
  confirm(
    message: string,
    options?: { type?: string; okText?: string; cancelText?: string },
  ): Promise<boolean>;
};
