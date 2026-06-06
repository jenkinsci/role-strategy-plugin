export function readBootstrap<T>(mountNode: HTMLElement, attrName: string): T {
  const raw = mountNode.getAttribute(attrName);
  if (!raw) {
    throw new Error(
      `Missing ${attrName} bootstrap attribute on #${mountNode.id}`,
    );
  }
  return JSON.parse(raw) as T;
}
