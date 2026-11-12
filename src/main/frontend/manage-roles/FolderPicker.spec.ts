import { describe, expect, it } from "vitest";
import { normalizeFolder, withPendingFolder } from "./FolderPicker.tsx";

describe("normalizeFolder", () => {
  it("strips surrounding whitespace and slashes", () => {
    expect(normalizeFolder("  /Team/Special/ ")).toBe("Team/Special");
  });

  it("keeps Unicode spaces that Java's String.trim keeps", () => {
    expect(normalizeFolder("\u00a0Team/Special\u2003")).toBe(
      "\u00a0Team/Special\u2003",
    );
    expect(normalizeFolder("\t/\u00a0Team/ ")).toBe("\u00a0Team");
  });
});

describe("withPendingFolder", () => {
  it("does not treat a name with a Unicode space as a duplicate", () => {
    expect(withPendingFolder(["Team"], "\u00a0Team")).toEqual([
      "Team",
      "\u00a0Team",
    ]);
  });
});
