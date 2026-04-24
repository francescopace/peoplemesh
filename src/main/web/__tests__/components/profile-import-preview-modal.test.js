import { describe, expect, it, vi } from "vitest";
import { showProfileImportPreviewModal } from "../../assets/js/components/profile-import-preview-modal.js";

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("profile-import-preview-modal component", () => {
  it("applies selected import payload and triggers onApplied callback", async () => {
    document.body.innerHTML = "";
    const onApply = vi.fn().mockResolvedValue({});
    const onApplied = vi.fn().mockResolvedValue(undefined);

    showProfileImportPreviewModal({
      imported: {
        identity: { birth_date: "1990-01-01" },
      },
      current: {
        identity: { birth_date: "1989-02-02" },
      },
      source: "github",
      onApply,
      onApplied,
    });

    const applyBtn = document.querySelector(".import-preview-footer .btn-primary");
    expect(applyBtn).not.toBeNull();
    applyBtn.click();
    await flushPromises();
    await flushPromises();

    expect(onApply).toHaveBeenCalledWith(
      "github",
      { identity: { birth_date: "1990-01-01" } },
      expect.objectContaining({ selectedCount: 1 })
    );
    expect(onApplied).toHaveBeenCalledTimes(1);
    expect(document.querySelector(".import-preview-dialog")).toBeNull();
  });
});
