import { el, toast } from "../ui.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";
import { buildFieldMap, buildPartialProfile } from "../services/profile-import-service.js";
import { formatProvenanceSource } from "./profile-fields.js";

export function showProfileImportPreviewModal({
  imported,
  current,
  source,
  onApply,
  onApplied,
}) {
  const fieldMap = buildFieldMap(imported, current);
  const selected = new Set();
  fieldMap.forEach((field) => {
    if (field.importedDisplay && field.importedDisplay !== "\u2014") selected.add(field.key);
  });
  const mergeModes = new Map();
  fieldMap.forEach((field) => {
    if (field.canMerge && field.hasConflict) mergeModes.set(field.key, "merge");
  });

  const sourceLabel = formatProvenanceSource(source);

  const overlay = el("div", { className: "modal-overlay import-preview-overlay" });
  const dialog = el("div", {
    className: "modal-dialog import-preview-dialog",
    role: "dialog",
    "aria-modal": "true",
    "aria-label": `Import from ${sourceLabel}`,
  });

  const header = el("div", { className: "modal-header" },
    el("div", { className: "import-preview-title-row" },
      el("span", { className: "material-symbols-outlined import-preview-icon" }, "sync_alt"),
      el("h3", {}, `Import from ${sourceLabel}`)
    ),
    el("button", {
      className: "modal-close btn btn-ghost",
      "aria-label": "Close",
      onClick: () => closeModal(),
    }, "\u00d7")
  );
  dialog.appendChild(header);

  const intro = el("div", { className: "import-preview-intro" },
    el("p", {},
      "Select which fields to import. Deselect any field you want to keep as-is."
    ),
    el("p", { className: "text-secondary" },
      "For list fields, we can merge new values with your current profile."
    )
  );

  const selectAllWrap = el("div", { className: "import-preview-select-all" });
  const selectAllCb = el("input", { type: "checkbox" });
  selectAllCb.checked = selected.size === fieldMap.filter((field) => field.importedDisplay && field.importedDisplay !== "\u2014").length;
  selectAllCb.addEventListener("change", () => {
    fieldMap.forEach((field) => {
      if (!field.importedDisplay || field.importedDisplay === "\u2014") return;
      if (selectAllCb.checked) selected.add(field.key);
      else selected.delete(field.key);
    });
    renderRows();
  });
  selectAllWrap.appendChild(el("label", { className: "import-preview-select-all-label" },
    selectAllCb,
    el("span", {}, "Select all")
  ));
  intro.appendChild(selectAllWrap);

  const body = el("div", { className: "modal-body import-preview-body" });
  body.appendChild(intro);

  const tableWrap = el("div", { className: "import-preview-table-wrap" });
  body.appendChild(tableWrap);

  function renderRows() {
    tableWrap.innerHTML = "";
    let currentSection = "";
    for (const field of fieldMap) {
      if (!field.importedDisplay || field.importedDisplay === "\u2014") continue;

      if (field.section !== currentSection) {
        currentSection = field.section;
        tableWrap.appendChild(el("div", { className: "import-preview-section" }, currentSection));
      }

      const isChecked = selected.has(field.key);
      const hasConflict = field.hasConflict;
      const mergeEnabled = mergeModes.get(field.key) === "merge";

      const row = el("div", {
        className: `import-preview-row${isChecked ? " import-preview-row--selected" : ""}${hasConflict ? " import-preview-row--conflict" : ""}${mergeEnabled ? " import-preview-row--merge" : ""}`,
        onClick: (e) => {
          if (e.target.tagName === "INPUT") return;
          if (e.target instanceof Element && e.target.closest(".import-preview-merge-label")) return;
          checkbox.checked = !checkbox.checked;
          checkbox.dispatchEvent(new Event("change"));
        },
      });

      const checkbox = el("input", { type: "checkbox", className: "import-preview-cb" });
      checkbox.checked = isChecked;
      checkbox.addEventListener("change", () => {
        if (checkbox.checked) selected.add(field.key);
        else selected.delete(field.key);
        row.classList.toggle("import-preview-row--selected", checkbox.checked);
        updateFooterCount();
      });

      const info = el("div", { className: "import-preview-field-info" });
      info.appendChild(el("div", { className: "import-preview-field-label" }, field.label));

      const values = el("div", { className: "import-preview-values" });

      if (field.currentDisplay && field.currentDisplay !== "\u2014") {
        const currentValue = el("div", { className: "import-preview-current" });
        currentValue.appendChild(el("span", { className: "import-preview-val-label" }, "Current"));
        currentValue.appendChild(el("span", { className: "import-preview-val" }, field.currentDisplay));
        if (field.currentProv) {
          currentValue.appendChild(el("span", { className: "import-preview-prov" }, formatProvenanceSource(field.currentProv)));
        }
        values.appendChild(currentValue);
      }

      const importedValue = el("div", { className: "import-preview-imported" });
      importedValue.appendChild(el("span", { className: "import-preview-val-label" }, "New"));
      importedValue.appendChild(el("span", { className: "import-preview-val import-preview-val--new" }, field.importedDisplay));
      values.appendChild(importedValue);

      if (field.canMerge && mergeEnabled && field.mergedDisplay && field.mergedDisplay !== "\u2014") {
        const merged = el("div", { className: "import-preview-result" });
        merged.appendChild(el("span", { className: "import-preview-val-label" }, "Result"));
        merged.appendChild(el("span", { className: "import-preview-val" }, field.mergedDisplay));
        values.appendChild(merged);
      }

      info.appendChild(values);

      if (field.canMerge) {
        const mergeLabel = el("label", { className: "import-preview-merge-label" });
        const mergeCb = el("input", { type: "checkbox", className: "import-preview-merge-cb" });
        mergeCb.checked = mergeEnabled;
        mergeCb.addEventListener("change", () => {
          if (mergeCb.checked) mergeModes.set(field.key, "merge");
          else mergeModes.delete(field.key);
          renderRows();
        });
        mergeLabel.appendChild(mergeCb);
        mergeLabel.appendChild(el("span", {}, "Merge with current values"));
        info.appendChild(mergeLabel);
      }

      row.appendChild(checkbox);
      row.appendChild(info);
      tableWrap.appendChild(row);
    }
  }

  renderRows();
  dialog.appendChild(body);

  const footer = el("div", { className: "modal-footer import-preview-footer" });
  const countSpan = el("span", { className: "import-preview-count text-secondary" });
  const cancelBtn = el("button", {
    className: "btn btn-secondary",
    onClick: () => closeModal(),
  }, "Cancel");
  const applyBtn = el("button", { className: "btn btn-primary" },
    el("span", { className: "material-symbols-outlined icon-18" }, "check"),
    el("span", {}, "Import & Save")
  );

  function updateFooterCount() {
    countSpan.textContent = `${selected.size} field${selected.size !== 1 ? "s" : ""} selected`;
  }
  updateFooterCount();

  applyBtn.addEventListener("click", async () => {
    if (selected.size === 0) {
      toast("No fields selected", "info");
      return;
    }
    applyBtn.disabled = true;
    cancelBtn.disabled = true;
    applyBtn.querySelector("span:last-child").textContent = "Saving...";
    try {
      const partial = buildPartialProfile(imported, current, selected, mergeModes);
      await onApply(source, partial, { selectedCount: selected.size, sourceLabel });
      toast(`Imported ${selected.size} field${selected.size !== 1 ? "s" : ""} from ${sourceLabel}`, "success");
      closeModal();
      if (onApplied) {
        await onApplied();
      }
    } catch (err) {
      toast(getUserFacingErrorMessage(err, "Could not import selected fields."), "error");
      applyBtn.disabled = false;
      cancelBtn.disabled = false;
      applyBtn.querySelector("span:last-child").textContent = "Import & Save";
    }
  });

  footer.appendChild(countSpan);
  footer.appendChild(cancelBtn);
  footer.appendChild(applyBtn);
  dialog.appendChild(footer);

  overlay.appendChild(dialog);
  overlay.addEventListener("click", (e) => { if (e.target === overlay) closeModal(); });
  document.body.appendChild(overlay);

  function closeModal() {
    overlay.remove();
  }
}
