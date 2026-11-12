import { type KeyboardEvent, useEffect, useId, useRef, useState } from "react";

import { CloseIcon } from "../common/components/icons/CloseIcon.tsx";

const MAX_SUGGESTIONS = 20;
// Waits for a pause in typing before asking the backend for suggestions.
const SUGGEST_DELAY_MS = 200;

interface FolderPickerProps {
  /** Id for the text input, so the dialog's form label can point at it. */
  inputId: string;
  folders: string[];
  onChange: (folders: string[]) => void;
  /**
   * Text typed but not added yet. The dialog owns it so that saving can
   * include a folder the user typed without pressing Enter.
   */
  input: string;
  onInputChange: (input: string) => void;
  /** Folder full names containing the query; empty query lists all. */
  loadFolders: (query: string, max: number) => Promise<string[]>;
}

/**
 * Mirrors Java's String.trim, which strips only characters up to U+0020.
 * JavaScript's trim also strips Unicode spaces such as U+00A0, which can be
 * part of an item name.
 */
function javaTrim(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && value.charCodeAt(start) <= 0x20) start++;
  while (end > start && value.charCodeAt(end - 1) <= 0x20) end--;
  return value.substring(start, end);
}

/**
 * Strips the slashes and whitespace the backend ignores (Role.normalizeFolders),
 * so duplicates are caught before saving and the saved name matches what REST
 * and CasC would store.
 */
export function normalizeFolder(value: string): string {
  return javaTrim(javaTrim(value).replace(/^\/+|\/+$/g, ""));
}

/** Adds the typed folder to the list unless it is blank or already listed. */
export function withPendingFolder(folders: string[], input: string): string[] {
  const pending = normalizeFolder(input);
  if (pending === "" || folders.includes(pending)) return folders;
  return [...folders, pending].sort();
}

/**
 * Chooses the folders a folder-scoped item role applies to. Suggestions come
 * from existing folders, but any full item name can be typed, so roles can be
 * set up before the folder exists.
 */
export function FolderPicker({
  inputId,
  folders,
  onChange,
  input,
  onInputChange,
  loadFolders,
}: FolderPickerProps) {
  const id = useId();
  const listId = `${id}-suggestions`;
  const inputRef = useRef<HTMLInputElement>(null);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  // Suggestions load once the field is used, not as soon as the dialog opens:
  // the unfiltered list is the most expensive query.
  const [active, setActive] = useState(false);
  const [announcement, setAnnouncement] = useState("");

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const result = await loadFolders(input.trim(), MAX_SUGGESTIONS);
        if (!cancelled) setSuggestions(result);
      } catch (err) {
        // Suggestions are a convenience; typing a name still works.
        console.error("Failed to load folder suggestions", err);
      }
    }, SUGGEST_DELAY_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [active, input, loadFolders]);

  const pending = normalizeFolder(input);
  const canAdd = pending !== "" && !folders.includes(pending);

  const add = () => {
    if (pending === "") return;
    // A folder that is already listed needs no change; just clear the field.
    if (canAdd) {
      onChange(withPendingFolder(folders, input));
      setAnnouncement(`Added ${pending}`);
    }
    onInputChange("");
  };

  const remove = (folder: string) => {
    onChange(folders.filter((f) => f !== folder));
    setAnnouncement(`Removed ${folder}`);
    // The clicked button is gone; keep focus inside the dialog.
    inputRef.current?.focus();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // Enter that confirms an IME composition must not add a half-typed name.
    if (e.key === "Enter" && !e.nativeEvent.isComposing) {
      // Enter adds the folder rather than submitting the role dialog.
      e.preventDefault();
      add();
    }
  };

  return (
    <div className="rsp-folder-picker">
      {folders.length > 0 && (
        <ul className="rsp-folder-picker__list" aria-label="Selected folders">
          {folders.map((folder) => (
            <li key={folder} className="rsp-folder-picker__chip">
              <span className="rsp-folder-picker__name">{folder}</span>
              <button
                type="button"
                className="rsp-folder-picker__remove"
                aria-label={`Remove ${folder}`}
                onClick={() => remove(folder)}
              >
                <CloseIcon />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="rsp-folder-picker__add">
        <input
          ref={inputRef}
          id={inputId}
          type="text"
          className="jenkins-input"
          list={listId}
          value={input}
          placeholder="For example Team/Special"
          onFocus={() => setActive(true)}
          onChange={(e) => onInputChange(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        <datalist id={listId}>
          {suggestions
            .filter((s) => !folders.includes(s))
            .map((s) => (
              <option key={s} value={s} />
            ))}
        </datalist>
        <button
          type="button"
          className="jenkins-button"
          onClick={add}
          disabled={!canAdd}
        >
          Add folder
        </button>
      </div>
      <div className="rsp-visually-hidden" role="status" aria-live="polite">
        {announcement}
      </div>
    </div>
  );
}
