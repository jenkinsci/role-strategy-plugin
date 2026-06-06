import { type ReactNode, useLayoutEffect, useRef } from "react";

import { CloseIcon } from "./icons/CloseIcon.tsx";

interface DialogProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  primaryAction?: ReactNode;
}

export function Dialog({
  title,
  onClose,
  children,
  primaryAction,
}: DialogProps) {
  const ref = useRef<HTMLDialogElement | null>(null);

  useLayoutEffect(() => {
    const node = ref.current;
    if (!node) return;
    if (!node.open) {
      node.showModal();
    }
    // The browser fires "cancel" when ESC is pressed; route it through onClose.
    const onCancel = (e: Event) => {
      e.preventDefault();
      onClose();
    };
    node.addEventListener("cancel", onCancel);
    return () => {
      node.removeEventListener("cancel", onCancel);
      if (node.open) node.close();
    };
  }, [onClose]);

  return (
    <dialog
      ref={ref}
      className="jenkins-dialog rsp-dialog"
      // No backdrop-click-to-close — the close button is the only dismissal.
    >
      <div className="rsp-dialog__header">
        <div className="jenkins-dialog__title">
          {title}{" "}
          <button
            type="button"
            aria-label="Close"
            className="jenkins-dialog__title__button jenkins-dialog__title__close-button jenkins-button"
            onClick={onClose}
          >
            <CloseIcon />
          </button>
        </div>
      </div>
      <div className="jenkins-dialog__contents">
        {children}
        {primaryAction && (
          <>
            <div className="jenkins-bottom-app-bar__shadow jenkins-bottom-app-bar__shadow--borderless jenkins-bottom-app-bar__shadow--stuck" />
            <div id="bottom-sticker" className="bottom-sticker">
              <div className="bottom-sticker-inner jenkins-buttons-row jenkins-buttons-row--equal-width">
                {primaryAction}
              </div>
            </div>
          </>
        )}
      </div>
    </dialog>
  );
}
