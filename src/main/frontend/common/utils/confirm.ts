interface JenkinsDialog {
  confirm: (
    message: string,
    options?: { type?: string; okText?: string; cancelText?: string },
  ) => Promise<boolean>;
}

/**
 * Wraps Jenkins core's window.dialog.confirm helper, falling back to window.confirm
 * if it's not loaded (mostly to keep unit tests trivial).
 */
export async function confirmAction(
  message: string,
  okText = "Delete",
): Promise<boolean> {
  const d = (window as unknown as { dialog?: JenkinsDialog }).dialog;
  if (d?.confirm) {
    try {
      return await d.confirm(message, { type: "destructive", okText });
    } catch {
      return false;
    }
  }
  return window.confirm(message);
}
