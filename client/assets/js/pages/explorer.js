import { apiRequest, checkHealth } from "../shared/api.js";
import {
    clearApiKey,
    clearLastPath,
    getApiKey,
    getIncludeHiddenPreference,
    getLastPath,
    getThemePreference,
    setIncludeHiddenPreference,
    setLastPath,
    setThemePreference
} from "../shared/storage.js";
import { createLoadingController, createToastManager } from "../shared/ui.js";
import {
    buildBreadcrumbs,
    describeEntryKind,
    escapeHtml,
    formatDate,
    formatSize,
    getParentPath,
    getPreviewType,
    normalizePath,
    previewLimits,
    splitTagInput
} from "../shared/utils.js";

const TEXT = {
    ready: "Ready",
    checking: "Checking session...",
    loadingDir: "Loading directory...",
    loadingDetail: "Loading detail...",
    uploading: "Uploading...",
    deleting: "Deleting...",
    creating: "Creating directory...",
    previewLoading: "Loading preview...",
    streamLoading: "Loading stream...",
    applyingTags: "Applying tags...",
    removingTags: "Removing tags...",
    noSelection: "No selection",
    noTags: "No tags",
    detailEmptyTitle: "Nothing selected",
    detailEmptySubtitle: "Select a file or folder to inspect metadata and preview content.",
    previewEmptyTitle: "Preview appears here",
    previewEmptySubtitle: "Select a file to load a quick preview or choose a folder to inspect its details.",
    previewDirectoryTitle: "Directory selected",
    previewDirectorySubtitle: "Use OPEN, double click, or breadcrumbs to move into this folder.",
    previewUnsupportedTitle: "Preview is not available",
    previewUnsupportedSubtitle: "Download the file or use a dedicated viewer when needed.",
    previewTooLargeText: "Text preview is limited to 500 KB.",
    previewTooLargeImage: "Image preview is limited to 20 MB.",
    previewTooLargeMedia: "Media preview is limited to 200 MB.",
    previewTooLargePdf: "PDF preview is limited to 50 MB.",
    fileTagHint: "Select a file to manage tags.",
    directoryTagHint: "Tags are only supported for files.",
    uploadEmpty: "No files selected yet.",
    unauthorized: "Session expired. Please log in again.",
    disconnected: "Disconnected.",
    downloadStarted: "Download started.",
    tagsApplied: "Tags applied.",
    tagsRemoved: "Selected tags removed.",
    chooseFolderName: "Enter a folder name.",
    chooseFiles: "Choose one or more files first.",
    chooseTags: "Select existing tags or type new tag names.",
    chooseAttachedTags: "Select attached tags to remove.",
    open: "Open",
    preview: "Preview",
    stream: "Stream",
    download: "Download",
    delete: "Delete",
    yes: "Yes",
    no: "No",
    uploadDestination: (path) => `Upload target: ${path}`,
    confirmDelete: (name) => `Delete "${name}"?`,
    created: (name) => `Created "${name}".`,
    deleted: (name) => `Deleted "${name}".`,
    uploaded: (count) => `Uploaded ${count} file(s).`,
    tagAttached: (count) => `${count} tag(s) attached`,
    previewFailed: (message) => `Preview failed: ${message}`
};

const DETAIL_LABELS = [
    ["Name", (detail) => detail.name || "-"],
    ["Path", (detail) => detail.relativePath || "-"],
    ["Type", (detail) => detail.entryType === "DIRECTORY" ? "Directory" : "File"],
    ["MIME", (detail) => detail.mimeType || "-"],
    ["Size", (detail) => detail.entryType === "DIRECTORY" ? "-" : formatSize(detail.sizeBytes)],
    ["Modified", (detail) => formatDate(detail.modifiedAt)],
    ["Created", (detail) => formatDate(detail.createdAtFs)],
    ["Hidden", (detail) => detail.hidden ? TEXT.yes : TEXT.no],
    ["File ID", (detail) => detail.fileId != null ? String(detail.fileId) : "-"]
];

const state = {
    apiKey: getApiKey(),
    includeHidden: getIncludeHiddenPreference(),
    theme: getThemePreference(),
    currentPath: getLastPath() || "/",
    currentEntries: [],
    availableTags: [],
    selectedEntry: null,
    selectedDetail: null,
    selectedTagIdsForRemoval: [],
    sortKey: "name",
    sortAsc: true,
    backStack: [],
    forwardStack: [],
    uploadFiles: [],
    previewUrl: null,
    previewRequestId: 0,
    pendingDeleteEntry: null
};

const elements = {
    loadingBar: document.getElementById("loading-bar"),
    toastContainer: document.getElementById("toast-container"),
    backButton: document.getElementById("backButton"),
    upButton: document.getElementById("upButton"),
    refreshButton: document.getElementById("refreshButton"),
    themeToggleButton: document.getElementById("themeToggleButton"),
    uploadButton: document.getElementById("uploadButton"),
    newFolderButton: document.getElementById("newFolderButton"),
    disconnectButton: document.getElementById("disconnectButton"),
    includeHiddenToggle: document.getElementById("includeHiddenToggle"),
    currentPathLabel: document.getElementById("currentPathLabel"),
    selectionSummary: document.getElementById("selectionSummary"),
    statusLabel: document.getElementById("statusLabel"),
    breadcrumbs: document.getElementById("breadcrumbs"),
    entryCountLabel: document.getElementById("entryCountLabel"),
    fileListPanel: document.getElementById("fileListPanel"),
    fileTableBody: document.getElementById("fileTableBody"),
    fileTable: document.getElementById("fileTable"),
    listLoading: document.getElementById("listLoading"),
    emptyState: document.getElementById("emptyState"),
    dropOverlay: document.getElementById("dropOverlay"),
    detailBadge: document.getElementById("detailBadge"),
    detailName: document.getElementById("detailName"),
    detailSubtitle: document.getElementById("detailSubtitle"),
    detailCloseButton: document.getElementById("detailCloseButton"),
    detailList: document.getElementById("detailList"),
    detailActions: document.getElementById("detailActions"),
    previewContent: document.getElementById("previewContent"),
    selectedTagList: document.getElementById("selectedTagList"),
    quickTagInput: document.getElementById("quickTagInput"),
    availableTagOptions: document.getElementById("availableTagOptions"),
    quickTagAddButton: document.getElementById("quickTagAddButton"),
    reloadDetailButton: document.getElementById("reloadDetailButton"),
    availableTagsSelect: document.getElementById("availableTagsSelect"),
    selectedTagSelection: document.getElementById("selectedTagSelection"),
    applySelectedTagsButton: document.getElementById("applySelectedTagsButton"),
    removeSelectedTagsButton: document.getElementById("removeSelectedTagsButton"),
    tagStatusLabel: document.getElementById("tagStatusLabel"),
    modalOverlay: document.getElementById("modalOverlay"),
    mkdirModal: document.getElementById("mkdirModal"),
    uploadModal: document.getElementById("uploadModal"),
    deleteModal: document.getElementById("deleteModal"),
    mkdirNameInput: document.getElementById("mkdirNameInput"),
    mkdirCancelButton: document.getElementById("mkdirCancelButton"),
    mkdirConfirmButton: document.getElementById("mkdirConfirmButton"),
    uploadDestination: document.getElementById("uploadDestination"),
    uploadDropZone: document.getElementById("uploadDropZone"),
    uploadInput: document.getElementById("uploadInput"),
    uploadFileList: document.getElementById("uploadFileList"),
    conflictPolicySelect: document.getElementById("conflictPolicySelect"),
    uploadCancelButton: document.getElementById("uploadCancelButton"),
    uploadConfirmButton: document.getElementById("uploadConfirmButton"),
    deleteTargetLabel: document.getElementById("deleteTargetLabel"),
    deleteCancelButton: document.getElementById("deleteCancelButton"),
    deleteConfirmButton: document.getElementById("deleteConfirmButton")
};

const loading = createLoadingController(elements.loadingBar);
const toast = createToastManager(elements.toastContainer);

function setStatus(message) {
    elements.statusLabel.textContent = message;
}

function showError(error) {
    if (error?.status === 401) {
        toast.show(TEXT.unauthorized, "error");
        disconnect();
        return;
    }

    toast.show(error?.message || String(error), "error");
}

function clearPreview() {
    if (state.previewUrl) {
        URL.revokeObjectURL(state.previewUrl);
        state.previewUrl = null;
    }
}

function disconnect() {
    clearApiKey();
    clearLastPath();
    clearPreview();
    window.location.href = "./index.html";
}

function updateNavigationButtons() {
    elements.backButton.disabled = state.backStack.length === 0;
    elements.upButton.disabled = state.currentPath === "/";
}

function applyTheme(theme) {
    state.theme = theme === "light" ? "light" : "dark";
    document.body.dataset.theme = state.theme;
    setThemePreference(state.theme);
    elements.themeToggleButton.textContent = state.theme === "light" ? "Dark Mode" : "Light Mode";
}

function renderSortHeaders() {
    elements.fileTable.querySelectorAll("th[data-sort-key]").forEach((header) => {
        const label = header.dataset.sortLabel;
        const isSorted = state.sortKey === header.dataset.sortKey;
        header.classList.add("sortable");
        header.classList.toggle("sorted", isSorted);
        header.textContent = isSorted ? `${label} ${state.sortAsc ? "ASC" : "DESC"}` : label;
    });
}

function sortEntries(entries) {
    return [...entries].sort((left, right) => {
        if (left.entryType !== right.entryType) {
            return left.entryType === "DIRECTORY" ? -1 : 1;
        }

        let leftValue = "";
        let rightValue = "";

        if (state.sortKey === "name") {
            leftValue = left.name.toLowerCase();
            rightValue = right.name.toLowerCase();
        } else if (state.sortKey === "type") {
            leftValue = describeEntryKind(left).toLowerCase();
            rightValue = describeEntryKind(right).toLowerCase();
        } else if (state.sortKey === "size") {
            leftValue = left.sizeBytes || 0;
            rightValue = right.sizeBytes || 0;
        } else if (state.sortKey === "modified") {
            leftValue = left.modifiedAt || "";
            rightValue = right.modifiedAt || "";
        }

        if (leftValue < rightValue) {
            return state.sortAsc ? -1 : 1;
        }
        if (leftValue > rightValue) {
            return state.sortAsc ? 1 : -1;
        }
        return 0;
    });
}

function renderBreadcrumbs() {
    elements.breadcrumbs.innerHTML = "";

    buildBreadcrumbs(state.currentPath).forEach((crumb) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "breadcrumb" + (crumb.path === state.currentPath ? " active" : "");
        button.textContent = crumb.label;
        button.disabled = crumb.path === state.currentPath;

        if (!button.disabled) {
            button.addEventListener("click", async () => {
                await navigateTo(crumb.path);
            });
        }

        elements.breadcrumbs.appendChild(button);
    });
}

function renderSelectionSummary() {
    if (!state.selectedEntry) {
        elements.selectionSummary.textContent = TEXT.noSelection;
        return;
    }

    elements.selectionSummary.textContent = `${state.selectedEntry.name} / ${describeEntryKind(state.selectedEntry)}`;
}

function clearSelection() {
    state.selectedEntry = null;
    state.selectedDetail = null;
    state.selectedTagIdsForRemoval = [];
    renderSelectionSummary();
    renderTable();
    renderEmptyDetail();
}

function renderTable() {
    const entries = sortEntries(state.currentEntries);
    elements.fileTableBody.innerHTML = "";
    elements.entryCountLabel.textContent = `${entries.length} item(s)`;
    elements.emptyState.classList.toggle("hidden", entries.length > 0);

    entries.forEach((entry) => {
        const row = document.createElement("tr");
        row.className = "entry-row";
        row.dataset.path = entry.relativePath;

        if (state.selectedEntry?.relativePath === entry.relativePath) {
            row.classList.add("selected");
        }

        const tagsHtml = (entry.tags || [])
            .slice(0, 3)
            .map((tag) => `<span class="tag-chip">${escapeHtml(tag.tagName)}</span>`)
            .join("");

        row.innerHTML = `
            <td>
                <div class="entry-name">
                    <span class="entry-badge ${entry.entryType === "DIRECTORY" ? "dir" : "file"}">${entry.entryType === "DIRECTORY" ? "DIR" : "FILE"}</span>
                    <div class="entry-text">
                        <div class="entry-title">${escapeHtml(entry.name)}</div>
                        <div class="entry-subtitle">${escapeHtml(entry.relativePath)}</div>
                    </div>
                </div>
            </td>
            <td>${escapeHtml(describeEntryKind(entry))}</td>
            <td class="cell-mono">${escapeHtml(entry.entryType === "DIRECTORY" ? "-" : formatSize(entry.sizeBytes))}</td>
            <td class="cell-mono">${escapeHtml(formatDate(entry.modifiedAt))}</td>
            <td><div class="table-tags">${tagsHtml || `<span class="tag-chip tag-muted">${TEXT.noTags}</span>`}</div></td>
        `;

        row.addEventListener("click", async () => {
            state.selectedEntry = entry;
            renderSelectionSummary();
            renderTable();
            await loadDetail(entry.relativePath);
        });

        row.addEventListener("dblclick", async () => {
            state.selectedEntry = entry;
            renderSelectionSummary();
            renderTable();

            if (entry.entryType === "DIRECTORY") {
                await navigateTo(entry.relativePath);
                return;
            }

            const detail = state.selectedDetail?.relativePath === entry.relativePath
                ? state.selectedDetail
                : await loadDetail(entry.relativePath);
            await renderPreview(detail, true);
        });

        elements.fileTableBody.appendChild(row);
    });
}

function renderPreviewPlaceholder(title, subtitle) {
    clearPreview();
    elements.previewContent.innerHTML = `
        <div class="preview-empty">
            <h3>${escapeHtml(title)}</h3>
            <p>${escapeHtml(subtitle)}</p>
        </div>
    `;
}

function renderDetailMeta(detail) {
    elements.detailList.innerHTML = DETAIL_LABELS.map(([label, resolver]) => `
        <div class="detail-list-row">
            <dt>${escapeHtml(label)}</dt>
            <dd>${escapeHtml(String(resolver(detail)))}</dd>
        </div>
    `).join("");
}

function renderSelectedTagRemovalState(attachedTags) {
    const selectedIds = new Set(state.selectedTagIdsForRemoval);
    const selectedTags = attachedTags.filter((tag) => selectedIds.has(tag.tagId));

    if (selectedTags.length === 0) {
        elements.selectedTagSelection.textContent = "No tags selected";
        return;
    }

    elements.selectedTagSelection.innerHTML = selectedTags
        .map((tag) => `<span class="tag-chip tag-selected">${escapeHtml(tag.tagName)}</span>`)
        .join("");
}

function renderTagControls(detail) {
    const isFile = detail?.entryType === "FILE";
    const attachedTags = isFile ? detail.tags || [] : [];
    const attachedIds = new Set(attachedTags.map((tag) => tag.tagId));
    const availableTags = isFile
        ? state.availableTags.filter((tag) => !attachedIds.has(tag.tagId))
        : [];

    elements.quickTagInput.value = "";
    elements.quickTagInput.disabled = !isFile;
    elements.quickTagAddButton.disabled = !isFile;
    elements.reloadDetailButton.disabled = !state.selectedEntry;
    elements.applySelectedTagsButton.disabled = !isFile;
    state.selectedTagIdsForRemoval = state.selectedTagIdsForRemoval.filter((tagId) => attachedIds.has(tagId));
    elements.removeSelectedTagsButton.disabled = !isFile || state.selectedTagIdsForRemoval.length === 0;

    if (!detail) {
        elements.selectedTagList.innerHTML = `<span class="tag-chip tag-muted">${TEXT.noSelection}</span>`;
        elements.availableTagsSelect.innerHTML = "";
        elements.availableTagOptions.innerHTML = "";
        elements.selectedTagSelection.textContent = "No tags selected";
        elements.tagStatusLabel.textContent = TEXT.fileTagHint;
        return;
    }

    if (!isFile) {
        elements.selectedTagList.innerHTML = `<span class="tag-chip tag-muted">Directory</span>`;
        elements.availableTagsSelect.innerHTML = "";
        elements.availableTagOptions.innerHTML = "";
        elements.selectedTagSelection.textContent = "No tags selected";
        elements.tagStatusLabel.textContent = TEXT.directoryTagHint;
        return;
    }

    elements.selectedTagList.innerHTML = attachedTags.length
        ? attachedTags.map((tag) => `
            <span class="tag-chip tag-selectable${state.selectedTagIdsForRemoval.includes(tag.tagId) ? " tag-selected" : ""}" data-attached-tag-id="${tag.tagId}">
                ${escapeHtml(tag.tagName)}
                <button class="tag-remove" type="button" data-tag-id="${tag.tagId}">x</button>
            </span>
        `).join("")
        : `<span class="tag-chip tag-muted">${TEXT.noTags}</span>`;

    elements.selectedTagList.querySelectorAll("[data-attached-tag-id]").forEach((chip) => {
        chip.addEventListener("click", () => {
            const tagId = Number(chip.dataset.attachedTagId);
            if (!Number.isFinite(tagId)) {
                return;
            }

            if (state.selectedTagIdsForRemoval.includes(tagId)) {
                state.selectedTagIdsForRemoval = state.selectedTagIdsForRemoval.filter((value) => value !== tagId);
            } else {
                state.selectedTagIdsForRemoval = [...state.selectedTagIdsForRemoval, tagId];
            }

            renderTagControls(state.selectedDetail);
        });
    });

    elements.selectedTagList.querySelectorAll("[data-tag-id]").forEach((button) => {
        button.addEventListener("click", async (event) => {
            event.stopPropagation();
            await removeTags([Number(button.dataset.tagId)]);
        });
    });

    elements.availableTagsSelect.innerHTML = availableTags
        .map((tag) => `<option value="${tag.tagId}">${escapeHtml(tag.tagName)}</option>`)
        .join("");

    elements.availableTagOptions.innerHTML = state.availableTags
        .map((tag) => `<option value="${escapeHtml(tag.tagName)}"></option>`)
        .join("");

    renderSelectedTagRemovalState(attachedTags);
    elements.tagStatusLabel.textContent = TEXT.tagAttached(attachedTags.length);
}

function makeActionButton(label, styleClass, handler) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `btn ${styleClass}`;
    button.textContent = label;
    button.addEventListener("click", handler);
    return button;
}

function renderDetailActions(detail) {
    elements.detailActions.innerHTML = "";

    if (!detail) {
        return;
    }

    if (detail.entryType === "DIRECTORY") {
        elements.detailActions.appendChild(makeActionButton(TEXT.open, "btn-subtle", async () => {
            await navigateTo(detail.relativePath);
        }));
    } else {
        elements.detailActions.appendChild(makeActionButton(TEXT.preview, "btn-subtle", async () => {
            await renderPreview(detail, false);
        }));
        elements.detailActions.appendChild(makeActionButton(TEXT.stream, "btn-subtle", async () => {
            await renderPreview(detail, true);
        }));
        elements.detailActions.appendChild(makeActionButton(TEXT.download, "btn-subtle", async () => {
            await downloadSelected(detail);
        }));
    }

    elements.detailActions.appendChild(makeActionButton(TEXT.delete, "btn-danger", () => {
        openDeleteModal(detail);
    }));
}

function renderEmptyDetail() {
    elements.detailBadge.textContent = "--";
    elements.detailName.textContent = TEXT.detailEmptyTitle;
    elements.detailSubtitle.textContent = TEXT.detailEmptySubtitle;
    elements.detailList.innerHTML = "";
    elements.detailActions.innerHTML = "";
    renderTagControls(null);
    renderPreviewPlaceholder(TEXT.previewEmptyTitle, TEXT.previewEmptySubtitle);
}

function renderDetail(detail) {
    if (!detail) {
        renderEmptyDetail();
        return;
    }

    elements.detailBadge.textContent = detail.entryType === "DIRECTORY" ? "DIR" : "FILE";
    elements.detailName.textContent = detail.name || TEXT.detailEmptyTitle;
    elements.detailSubtitle.textContent = detail.relativePath || TEXT.detailEmptySubtitle;
    renderDetailMeta(detail);
    renderDetailActions(detail);
    renderTagControls(detail);
}

async function renderPreview(detail, useStreamEndpoint = false) {
    clearPreview();

    if (!detail) {
        renderPreviewPlaceholder(TEXT.previewEmptyTitle, TEXT.previewEmptySubtitle);
        return;
    }

    if (detail.entryType === "DIRECTORY") {
        renderPreviewPlaceholder(TEXT.previewDirectoryTitle, TEXT.previewDirectorySubtitle);
        return;
    }

    const previewType = getPreviewType(detail);
    if (previewType === "none") {
        renderPreviewPlaceholder(TEXT.previewUnsupportedTitle, TEXT.previewUnsupportedSubtitle);
        return;
    }

    if (previewType === "text" && detail.sizeBytes > previewLimits.text) {
        renderPreviewPlaceholder(TEXT.previewUnsupportedTitle, TEXT.previewTooLargeText);
        return;
    }
    if (previewType === "image" && detail.sizeBytes > previewLimits.image) {
        renderPreviewPlaceholder(TEXT.previewUnsupportedTitle, TEXT.previewTooLargeImage);
        return;
    }
    if ((previewType === "video" || previewType === "audio") && detail.sizeBytes > previewLimits.media) {
        renderPreviewPlaceholder(TEXT.previewUnsupportedTitle, TEXT.previewTooLargeMedia);
        return;
    }
    if (previewType === "pdf" && detail.sizeBytes > previewLimits.pdf) {
        renderPreviewPlaceholder(TEXT.previewUnsupportedTitle, TEXT.previewTooLargePdf);
        return;
    }

    const requestId = ++state.previewRequestId;
    const statusText = useStreamEndpoint ? TEXT.streamLoading : TEXT.previewLoading;
    setStatus(statusText);
    elements.previewContent.innerHTML = `
        <div class="preview-message">
            <div class="spinner"></div>
            <p>${escapeHtml(statusText)}</p>
        </div>
    `;

    try {
        const endpoint = useStreamEndpoint ? "/files/stream" : "/files/download";
        const query = `?path=${encodeURIComponent(detail.relativePath)}`;

        if (previewType === "text") {
            const text = await apiRequest(`${endpoint}${query}`, { responseType: "text" });
            if (requestId !== state.previewRequestId) {
                return;
            }

            elements.previewContent.innerHTML = `<pre class="preview-text">${escapeHtml(text)}</pre>`;
            setStatus(TEXT.ready);
            return;
        }

        const blob = await apiRequest(`${endpoint}${query}`, { responseType: "blob" });
        if (requestId !== state.previewRequestId) {
            return;
        }

        state.previewUrl = URL.createObjectURL(blob);

        if (previewType === "image") {
            elements.previewContent.innerHTML = `<img class="preview-image" alt="${escapeHtml(detail.name)}">`;
            elements.previewContent.querySelector("img").src = state.previewUrl;
        } else if (previewType === "video") {
            elements.previewContent.innerHTML = `<video class="preview-video" controls></video>`;
            elements.previewContent.querySelector("video").src = state.previewUrl;
        } else if (previewType === "audio") {
            elements.previewContent.innerHTML = `<audio class="preview-audio" controls></audio>`;
            elements.previewContent.querySelector("audio").src = state.previewUrl;
        } else if (previewType === "pdf") {
            elements.previewContent.innerHTML = `<iframe class="preview-frame" title="PDF preview"></iframe>`;
            elements.previewContent.querySelector("iframe").src = state.previewUrl;
        }

        setStatus(TEXT.ready);
    } catch (error) {
        if (requestId !== state.previewRequestId) {
            return;
        }

        elements.previewContent.innerHTML = `
            <div class="preview-message">
                <p>${escapeHtml(TEXT.previewFailed(error.message))}</p>
            </div>
        `;
        showError(error);
    }
}

async function loadTags() {
    const response = await apiRequest("/tags");
    state.availableTags = response.tags || [];
}

async function loadDirectory(path, options = {}) {
    const normalizedPath = normalizePath(path);
    const previousSelectionPath = options.preserveSelection ? state.selectedEntry?.relativePath : null;

    setStatus(TEXT.loadingDir);
    elements.listLoading.classList.remove("hidden");
    elements.emptyState.classList.add("hidden");
    loading.start();

    try {
        const response = await apiRequest(`/entries?path=${encodeURIComponent(normalizedPath)}&includeHidden=${encodeURIComponent(String(state.includeHidden))}`);
        state.currentPath = response.currentPath;
        setLastPath(state.currentPath);
        state.currentEntries = response.entries || [];
        state.selectedEntry = previousSelectionPath
            ? state.currentEntries.find((entry) => entry.relativePath === previousSelectionPath) || null
            : null;
        state.selectedDetail = null;

        elements.currentPathLabel.textContent = state.currentPath;
        renderBreadcrumbs();
        renderSelectionSummary();
        renderTable();

        if (state.selectedEntry) {
            await loadDetail(state.selectedEntry.relativePath);
        } else {
            renderEmptyDetail();
        }
    } catch (error) {
        state.currentEntries = [];
        renderTable();
        renderEmptyDetail();
        showError(error);
    } finally {
        elements.listLoading.classList.add("hidden");
        loading.end();
        updateNavigationButtons();
        setStatus(TEXT.ready);
    }
}

async function navigateTo(path) {
    const nextPath = normalizePath(path);
    if (nextPath === state.currentPath) {
        await loadDirectory(nextPath, { preserveSelection: true });
        return;
    }

    state.backStack.push(state.currentPath);
    state.forwardStack = [];
    await loadDirectory(nextPath);
}

async function loadDetail(path) {
    setStatus(TEXT.loadingDetail);
    loading.start();

    try {
        const detail = await apiRequest(`/entries/detail?path=${encodeURIComponent(path)}`);
        state.selectedDetail = detail;
        renderDetail(detail);
        await renderPreview(detail, false);
        return detail;
    } catch (error) {
        state.selectedDetail = null;
        renderEmptyDetail();
        showError(error);
        throw error;
    } finally {
        loading.end();
        setStatus(TEXT.ready);
    }
}

function openModal(modalElement) {
    document.body.classList.add("modal-open");
    elements.modalOverlay.classList.remove("hidden");
    [elements.mkdirModal, elements.uploadModal, elements.deleteModal].forEach((modal) => {
        modal.classList.add("hidden");
    });
    modalElement.classList.remove("hidden");
}

function closeModal() {
    document.body.classList.remove("modal-open");
    elements.modalOverlay.classList.add("hidden");
    [elements.mkdirModal, elements.uploadModal, elements.deleteModal].forEach((modal) => {
        modal.classList.add("hidden");
    });
}

function openDeleteModal(entry) {
    state.pendingDeleteEntry = entry;
    elements.deleteTargetLabel.textContent = TEXT.confirmDelete(entry.name);
    openModal(elements.deleteModal);
}

function openMkdirModal() {
    elements.mkdirNameInput.value = "";
    openModal(elements.mkdirModal);
    window.setTimeout(() => elements.mkdirNameInput.focus(), 40);
}

function renderUploadFiles() {
    if (state.uploadFiles.length === 0) {
        elements.uploadFileList.innerHTML = `<div class="upload-file-item">${escapeHtml(TEXT.uploadEmpty)}</div>`;
        return;
    }

    elements.uploadFileList.innerHTML = state.uploadFiles
        .map((file) => `
            <div class="upload-file-item">
                <span>${escapeHtml(file.name)}</span>
                <span>${escapeHtml(formatSize(file.size))}</span>
            </div>
        `)
        .join("");
}

function openUploadModal(preloadedFiles = []) {
    state.uploadFiles = [...preloadedFiles];
    elements.uploadInput.value = "";
    elements.uploadDestination.textContent = TEXT.uploadDestination(state.currentPath);
    renderUploadFiles();
    openModal(elements.uploadModal);
}

async function createDirectory() {
    const name = elements.mkdirNameInput.value.trim();
    if (!name) {
        toast.show(TEXT.chooseFolderName, "error");
        return;
    }

    closeModal();
    setStatus(TEXT.creating);
    loading.start();

    try {
        await apiRequest("/directories", {
            method: "POST",
            json: {
                parentPath: state.currentPath,
                name
            }
        });

        toast.show(TEXT.created(name), "success");
        await loadDirectory(state.currentPath);
    } catch (error) {
        showError(error);
    } finally {
        loading.end();
        setStatus(TEXT.ready);
    }
}

async function uploadFiles() {
    if (state.uploadFiles.length === 0) {
        toast.show(TEXT.chooseFiles, "error");
        return;
    }

    closeModal();
    setStatus(TEXT.uploading);
    loading.start();

    let uploadedCount = 0;

    try {
        for (const file of state.uploadFiles) {
            const formData = new FormData();
            formData.append("parentPath", state.currentPath);
            formData.append("conflictPolicy", elements.conflictPolicySelect.value);
            formData.append("file", file);

            await apiRequest("/files/upload", {
                method: "POST",
                body: formData
            });
            uploadedCount += 1;
        }

        toast.show(TEXT.uploaded(uploadedCount), "success");
        await loadDirectory(state.currentPath);
    } catch (error) {
        showError(error);
    } finally {
        state.uploadFiles = [];
        renderUploadFiles();
        loading.end();
        setStatus(TEXT.ready);
    }
}

async function deleteEntry() {
    if (!state.pendingDeleteEntry) {
        return;
    }

    const target = state.pendingDeleteEntry;
    closeModal();
    setStatus(TEXT.deleting);
    loading.start();

    try {
        await apiRequest("/entries", {
            method: "DELETE",
            json: { path: target.relativePath }
        });

        toast.show(TEXT.deleted(target.name), "success");
        state.pendingDeleteEntry = null;
        clearSelection();
        await loadDirectory(state.currentPath);
    } catch (error) {
        showError(error);
    } finally {
        loading.end();
        setStatus(TEXT.ready);
    }
}

async function downloadSelected(detail) {
    if (!detail || detail.entryType !== "FILE") {
        return;
    }

    loading.start();

    try {
        const blob = await apiRequest(`/files/download?path=${encodeURIComponent(detail.relativePath)}`, {
            responseType: "blob"
        });
        const objectUrl = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = objectUrl;
        anchor.download = detail.name;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1200);
        toast.show(TEXT.downloadStarted, "success");
    } catch (error) {
        showError(error);
    } finally {
        loading.end();
        setStatus(TEXT.ready);
    }
}

async function applyTags(tagIds, tagNames) {
    if (!state.selectedDetail || state.selectedDetail.entryType !== "FILE") {
        return;
    }

    if (tagIds.length === 0 && tagNames.length === 0) {
        toast.show(TEXT.chooseTags, "error");
        return;
    }

    setStatus(TEXT.applyingTags);
    loading.start();

    try {
        const response = await apiRequest("/files/tags", {
            method: "POST",
            json: {
                path: state.selectedDetail.relativePath,
                tagIds,
                tagNames
            }
        });

        state.selectedDetail.tags = response.tags || [];
        const listEntry = state.currentEntries.find((entry) => entry.relativePath === state.selectedDetail.relativePath);
        if (listEntry) {
            listEntry.tags = response.tags || [];
        }

        state.selectedTagIdsForRemoval = [];
        await loadTags();
        renderTable();
        renderTagControls(state.selectedDetail);
        toast.show(TEXT.tagsApplied, "success");
    } catch (error) {
        showError(error);
    } finally {
        loading.end();
        setStatus(TEXT.ready);
    }
}

async function removeTags(tagIds) {
    if (!state.selectedDetail || state.selectedDetail.entryType !== "FILE") {
        return;
    }

    if (tagIds.length === 0) {
        toast.show(TEXT.chooseAttachedTags, "error");
        return;
    }

    setStatus(TEXT.removingTags);
    loading.start();

    try {
        const response = await apiRequest("/files/tags", {
            method: "DELETE",
            json: {
                path: state.selectedDetail.relativePath,
                tagIds
            }
        });

        state.selectedDetail.tags = response.tags || [];
        const listEntry = state.currentEntries.find((entry) => entry.relativePath === state.selectedDetail.relativePath);
        if (listEntry) {
            listEntry.tags = response.tags || [];
        }

        state.selectedTagIdsForRemoval = state.selectedTagIdsForRemoval.filter((tagId) =>
            (response.tags || []).some((tag) => tag.tagId === tagId)
        );
        renderTable();
        renderTagControls(state.selectedDetail);
        toast.show(TEXT.tagsRemoved, "success");
    } catch (error) {
        showError(error);
    } finally {
        loading.end();
        setStatus(TEXT.ready);
    }
}

async function quickAddTags() {
    const names = splitTagInput(elements.quickTagInput.value.trim());
    if (names.length === 0) {
        toast.show(TEXT.chooseTags, "error");
        return;
    }

    const attachedIds = new Set((state.selectedDetail?.tags || []).map((tag) => tag.tagId));
    const existingNameToId = new Map(state.availableTags.map((tag) => [tag.tagName.toLowerCase(), tag.tagId]));
    const tagIds = [];
    const tagNames = [];

    names.forEach((name) => {
        const existingId = existingNameToId.get(name.toLowerCase());
        if (existingId && !attachedIds.has(existingId)) {
            tagIds.push(existingId);
        } else if (!existingId) {
            tagNames.push(name);
        }
    });

    await applyTags(tagIds, tagNames);
    elements.quickTagInput.value = "";
}

function getSelectedTagIds(selectElement) {
    return Array.from(selectElement.selectedOptions)
        .map((option) => Number(option.value))
        .filter((value) => Number.isFinite(value) && value > 0);
}

async function verifySession() {
    setStatus(TEXT.checking);
    loading.start();

    try {
        await checkHealth();
        await apiRequest(`/entries?path=${encodeURIComponent("/")}&includeHidden=${encodeURIComponent(String(state.includeHidden))}`);
    } finally {
        loading.end();
        setStatus(TEXT.ready);
    }
}

function bindEvents() {
    elements.backButton.addEventListener("click", async () => {
        if (state.backStack.length === 0) {
            return;
        }

        state.forwardStack.push(state.currentPath);
        const previousPath = state.backStack.pop();
        await loadDirectory(previousPath);
    });

    elements.upButton.addEventListener("click", async () => {
        await navigateTo(getParentPath(state.currentPath));
    });

    elements.refreshButton.addEventListener("click", async () => {
        await loadDirectory(state.currentPath, { preserveSelection: true });
    });

    elements.themeToggleButton.addEventListener("click", () => {
        applyTheme(state.theme === "light" ? "dark" : "light");
    });

    elements.uploadButton.addEventListener("click", () => {
        openUploadModal();
    });

    elements.newFolderButton.addEventListener("click", () => {
        openMkdirModal();
    });

    elements.disconnectButton.addEventListener("click", () => {
        toast.show(TEXT.disconnected, "info");
        disconnect();
    });

    elements.includeHiddenToggle.checked = state.includeHidden;
    elements.includeHiddenToggle.addEventListener("change", async (event) => {
        state.includeHidden = event.target.checked;
        setIncludeHiddenPreference(state.includeHidden);
        await loadDirectory(state.currentPath, { preserveSelection: true });
    });

    elements.detailCloseButton.addEventListener("click", () => {
        clearSelection();
    });

    elements.reloadDetailButton.addEventListener("click", async () => {
        if (state.selectedEntry) {
            await loadDetail(state.selectedEntry.relativePath);
        }
    });

    elements.quickTagAddButton.addEventListener("click", async () => {
        await quickAddTags();
    });

    elements.quickTagInput.addEventListener("keydown", async (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            await quickAddTags();
        }
    });

    elements.applySelectedTagsButton.addEventListener("click", async () => {
        await applyTags(getSelectedTagIds(elements.availableTagsSelect), []);
    });

    elements.removeSelectedTagsButton.addEventListener("click", async () => {
        await removeTags([...state.selectedTagIdsForRemoval]);
    });

    elements.mkdirCancelButton.addEventListener("click", closeModal);
    elements.mkdirConfirmButton.addEventListener("click", async () => {
        await createDirectory();
    });

    elements.mkdirNameInput.addEventListener("keydown", async (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            await createDirectory();
        }
    });

    elements.uploadCancelButton.addEventListener("click", closeModal);
    elements.uploadConfirmButton.addEventListener("click", async () => {
        await uploadFiles();
    });

    elements.uploadDropZone.addEventListener("click", () => {
        elements.uploadInput.click();
    });

    elements.uploadInput.addEventListener("change", () => {
        state.uploadFiles = Array.from(elements.uploadInput.files || []);
        renderUploadFiles();
    });

    ["dragenter", "dragover"].forEach((eventName) => {
        elements.uploadDropZone.addEventListener(eventName, (event) => {
            event.preventDefault();
            elements.uploadDropZone.classList.add("dragover");
        });

        elements.fileListPanel.addEventListener(eventName, (event) => {
            event.preventDefault();
            elements.dropOverlay.classList.remove("hidden");
        });
    });

    ["dragleave", "dragend", "drop"].forEach((eventName) => {
        elements.uploadDropZone.addEventListener(eventName, (event) => {
            event.preventDefault();
            elements.uploadDropZone.classList.remove("dragover");
        });

        elements.fileListPanel.addEventListener(eventName, (event) => {
            event.preventDefault();
            if (eventName !== "drop") {
                elements.dropOverlay.classList.add("hidden");
            }
        });
    });

    elements.uploadDropZone.addEventListener("drop", (event) => {
        state.uploadFiles = Array.from(event.dataTransfer?.files || []);
        renderUploadFiles();
    });

    elements.fileListPanel.addEventListener("drop", (event) => {
        const files = Array.from(event.dataTransfer?.files || []);
        elements.dropOverlay.classList.add("hidden");

        if (files.length > 0) {
            openUploadModal(files);
        }
    });

    elements.deleteCancelButton.addEventListener("click", closeModal);
    elements.deleteConfirmButton.addEventListener("click", async () => {
        await deleteEntry();
    });

    elements.modalOverlay.addEventListener("click", (event) => {
        if (event.target === elements.modalOverlay) {
            closeModal();
        }
    });

    elements.fileTable.querySelectorAll("th[data-sort-key]").forEach((header) => {
        header.addEventListener("click", () => {
            const nextKey = header.dataset.sortKey;
            if (state.sortKey === nextKey) {
                state.sortAsc = !state.sortAsc;
            } else {
                state.sortKey = nextKey;
                state.sortAsc = true;
            }

            renderSortHeaders();
            renderTable();
        });
    });

    document.addEventListener("keydown", async (event) => {
        if (event.key === "Escape") {
            if (!elements.modalOverlay.classList.contains("hidden")) {
                closeModal();
                return;
            }

            if (state.selectedEntry) {
                clearSelection();
            }
        }

        if (event.key === "F5") {
            event.preventDefault();
            await loadDirectory(state.currentPath, { preserveSelection: true });
        }
    });
}

async function init() {
    if (!state.apiKey) {
        window.location.href = "./index.html";
        return;
    }

    applyTheme(state.theme);
    renderSortHeaders();
    renderSelectionSummary();
    renderEmptyDetail();
    bindEvents();

    try {
        await verifySession();
        await loadTags();
        await loadDirectory(state.currentPath);
    } catch (error) {
        showError(error);
    }
}

init();
