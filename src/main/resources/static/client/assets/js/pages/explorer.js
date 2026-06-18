import { apiRequest, checkHealth, verifyApiKey } from "../shared/api.js";
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
    previewLoading: "미리보기를 불러오는 중...",
    streamLoading: "스트림을 불러오는 중...",
    noSelection: "선택 없음",
    noTags: "Tags 없음",
    detailEmptyTitle: "선택된 항목 없음",
    detailEmptySubtitle: "파일이나 폴더를 선택하면 Metadata와 미리보기를 확인할 수 있습니다.",
    previewEmptyTitle: "미리보기 영역",
    previewEmptySubtitle: "파일을 선택하면 미리보기를, 폴더를 선택하면 상세 정보를 표시합니다.",
    previewDirectoryTitle: "폴더가 선택되었습니다",
    previewDirectorySubtitle: "열기 버튼이나 더블클릭 또는 경로 탐색을 이용해 폴더로 이동하세요.",
    previewUnsupportedTitle: "미리보기를 지원하지 않습니다",
    previewUnsupportedSubtitle: "파일을 다운로드하거나 전용 뷰어를 이용해 주세요.",
    previewTooLargeText: "텍스트 미리보기는 500 KB까지 지원합니다.",
    previewTooLargeImage: "이미지 미리보기는 20 MB까지 지원합니다.",
    previewTooLargeMedia: "미디어 미리보기는 200 MB까지 지원합니다.",
    previewTooLargePdf: "PDF 미리보기는 50 MB까지 지원합니다.",
    fileTagHint: "파일을 선택하면 Tags를 관리할 수 있습니다.",
    directoryTagHint: "Tags는 파일에만 적용할 수 있습니다.",
    uploadEmpty: "선택된 파일이 없습니다.",
    unauthorized: "세션이 만료되었습니다. 다시 로그인해 주세요.",
    disconnected: "연결을 해제했습니다.",
    downloadStarted: "다운로드를 시작했습니다.",
    tagsApplied: "Tags를 적용했습니다.",
    tagsRemoved: "Tags를 제거했습니다.",
    chooseFolderName: "폴더 이름을 입력해 주세요.",
    chooseFiles: "먼저 파일을 하나 이상 선택해 주세요.",
    chooseTags: "기존 Tags를 선택하거나 새 이름을 입력해 주세요.",
    open: "열기",
    preview: "미리보기",
    stream: "스트림",
    download: "다운로드",
    delete: "삭제",
    yes: "예",
    no: "아니요",
    uploadDestination: (path) => `업로드 위치: ${path}`,
    confirmDelete: (name) => `"${name}" 항목을 삭제할까요?`,
    created: (name) => `"${name}" 폴더를 만들었습니다.`,
    deleted: (name) => `"${name}" 항목을 삭제했습니다.`,
    uploaded: (count) => `파일 ${count}개를 업로드했습니다.`,
    tagAttached: (count) => `Tags ${count}개가 적용됨`,
    previewFailed: (message) => `미리보기 실패: ${message}`
};

const DETAIL_LABELS = [
    ["이름", (detail) => detail.name || "-"],
    ["경로", (detail) => detail.relativePath || "-"],
    ["유형", (detail) => detail.entryType === "DIRECTORY" ? "폴더" : "파일"],
    ["MIME", (detail) => detail.mimeType || "-"],
    ["크기", (detail) => detail.entryType === "DIRECTORY" ? "-" : formatSize(detail.sizeBytes)],
    ["수정일", (detail) => formatDate(detail.modifiedAt)],
    ["생성일", (detail) => formatDate(detail.createdAtFs)],
    ["숨김", (detail) => detail.hidden ? TEXT.yes : TEXT.no],
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
    availableTagsSelect: document.getElementById("availableTagsSelect"),
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
    elements.themeToggleButton.textContent = state.theme === "light" ? "다크 모드" : "라이트 모드";
}

function renderSortHeaders() {
    elements.fileTable.querySelectorAll("th[data-sort-key]").forEach((header) => {
        const label = header.dataset.sortLabel;
        const isSorted = state.sortKey === header.dataset.sortKey;
        header.classList.add("sortable");
        header.classList.toggle("sorted", isSorted);
        header.textContent = isSorted ? `${label} ${state.sortAsc ? "오름차순" : "내림차순"}` : label;
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

function clearSelection() {
    state.selectedEntry = null;
    state.selectedDetail = null;
    renderTable();
    renderEmptyDetail();
}

function renderTable() {
    const entries = sortEntries(state.currentEntries);
    elements.fileTableBody.innerHTML = "";
    elements.entryCountLabel.textContent = `${entries.length}개`;
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

        let clickTimer = null;

        row.addEventListener("click", () => {
            window.clearTimeout(clickTimer);
            clickTimer = window.setTimeout(async () => {
                state.selectedEntry = entry;
                renderTable();
                await loadDetail(entry.relativePath);
            }, 180);
        });

        row.addEventListener("dblclick", async () => {
            window.clearTimeout(clickTimer);
            state.selectedEntry = entry;
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
    elements.availableTagsSelect.disabled = !isFile;

    if (!detail) {
        elements.selectedTagList.innerHTML = `<span class="tag-chip tag-muted">${TEXT.noSelection}</span>`;
        elements.availableTagsSelect.innerHTML = "";
        elements.availableTagOptions.innerHTML = "";
        elements.tagStatusLabel.textContent = TEXT.fileTagHint;
        return;
    }

    if (!isFile) {
        elements.selectedTagList.innerHTML = `<span class="tag-chip tag-muted">폴더</span>`;
        elements.availableTagsSelect.innerHTML = "";
        elements.availableTagOptions.innerHTML = "";
        elements.tagStatusLabel.textContent = TEXT.directoryTagHint;
        return;
    }

    elements.selectedTagList.innerHTML = attachedTags.length
        ? attachedTags.map((tag) => `
            <span class="tag-chip">
                ${escapeHtml(tag.tagName)}
                <button class="tag-remove" type="button" data-tag-id="${tag.tagId}" aria-label="${escapeHtml(tag.tagName)} Tag 제거">x</button>
            </span>
        `).join("")
        : `<span class="tag-chip tag-muted">${TEXT.noTags}</span>`;

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

        renderBreadcrumbs();
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
    }
}

async function uploadFiles() {
    if (state.uploadFiles.length === 0) {
        toast.show(TEXT.chooseFiles, "error");
        return;
    }

    closeModal();
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
    }
}

async function deleteEntry() {
    if (!state.pendingDeleteEntry) {
        return;
    }

    const target = state.pendingDeleteEntry;
    closeModal();
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

        await loadTags();
        renderTable();
        renderTagControls(state.selectedDetail);
        toast.show(TEXT.tagsApplied, "success");
    } catch (error) {
        showError(error);
    } finally {
        loading.end();
    }
}

async function removeTags(tagIds) {
    if (!state.selectedDetail || state.selectedDetail.entryType !== "FILE") {
        return;
    }

    if (tagIds.length === 0) {
        return;
    }

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

        renderTable();
        renderTagControls(state.selectedDetail);
        toast.show(TEXT.tagsRemoved, "success");
    } catch (error) {
        showError(error);
    } finally {
        loading.end();
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

async function verifySession() {
    loading.start();

    try {
        await checkHealth();
        await verifyApiKey(state.apiKey);
    } finally {
        loading.end();
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

    elements.quickTagAddButton.addEventListener("click", async () => {
        await quickAddTags();
    });

    elements.quickTagInput.addEventListener("keydown", async (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            await quickAddTags();
        }
    });

    elements.availableTagsSelect.addEventListener("dblclick", async () => {
        const tagId = Number(elements.availableTagsSelect.value);
        if (Number.isFinite(tagId) && tagId > 0) {
            await applyTags([tagId], []);
        }
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
