(function () {
    const storageKeys = {
        apiKey: "rdm.apiKey",
        theme: "rdm.theme"
    };
    const apiKeyHeaderName = "API-Key";
    const previewableTextExtensions = new Set([
        "txt", "md", "markdown", "json", "xml", "html", "css", "js", "ts", "tsx", "jsx",
        "java", "kt", "yml", "yaml", "properties", "sql", "csv", "log"
    ]);

    migrateApiKeyStorage();

    const state = {
        apiKey: sessionStorage.getItem(storageKeys.apiKey) || "",
        theme: localStorage.getItem(storageKeys.theme) || "light",
        includeHidden: true,
        currentPath: "/",
        currentEntries: [],
        selectedEntry: null,
        selectedDetail: null,
        availableTags: [],
        backStack: [],
        forwardStack: [],
        previewUrl: null,
        previewRequestId: 0
    };

    const elements = {
        apiKeyInput: document.getElementById("apiKeyInput"),
        connectButton: document.getElementById("connectButton"),
        includeHiddenToggle: document.getElementById("includeHiddenToggle"),
        themeToggle: document.getElementById("themeToggle"),
        currentPathLabel: document.getElementById("currentPathLabel"),
        selectionSummary: document.getElementById("selectionSummary"),
        statusLabel: document.getElementById("statusLabel"),
        breadcrumbs: document.getElementById("breadcrumbs"),
        backButton: document.getElementById("backButton"),
        upButton: document.getElementById("upButton"),
        refreshButton: document.getElementById("refreshButton"),
        newFolderButton: document.getElementById("newFolderButton"),
        conflictPolicySelect: document.getElementById("conflictPolicySelect"),
        uploadButton: document.getElementById("uploadButton"),
        fileInput: document.getElementById("fileInput"),
        downloadButton: document.getElementById("downloadButton"),
        deleteButton: document.getElementById("deleteButton"),
        previewButton: document.getElementById("previewButton"),
        streamButton: document.getElementById("streamButton"),
        detailButton: document.getElementById("detailButton"),
        entryCountLabel: document.getElementById("entryCountLabel"),
        entryTableBody: document.getElementById("entryTableBody"),
        emptyState: document.getElementById("emptyState"),
        previewContent: document.getElementById("previewContent"),
        previewKindLabel: document.getElementById("previewKindLabel"),
        detailList: document.getElementById("detailList"),
        dropZone: document.getElementById("dropZone"),
        selectedTagList: document.getElementById("selectedTagList"),
        attachedTagsSelect: document.getElementById("attachedTagsSelect"),
        existingTagsSelect: document.getElementById("existingTagsSelect"),
        newTagsInput: document.getElementById("newTagsInput"),
        applyTagsButton: document.getElementById("applyTagsButton"),
        removeTagsButton: document.getElementById("removeTagsButton"),
        tagStatusLabel: document.getElementById("tagStatusLabel"),
        toast: document.getElementById("toast")
    };

    init();

    function init() {
        document.body.dataset.theme = state.theme;
        elements.apiKeyInput.value = state.apiKey;
        elements.includeHiddenToggle.checked = state.includeHidden;
        elements.themeToggle.textContent = state.theme === "dark" ? "Light Mode" : "Dark Mode";

        bindEvents();
        renderBreadcrumbs();
        renderSelection();
        renderDetail(null);
        updateNavigationButtons();
        updateTagControls();

        if (state.apiKey) {
            connect().catch(handleError);
        } else {
            renderUnsupportedState("Enter API Key and connect to start browsing.");
        }
    }

    function migrateApiKeyStorage() {
        const legacyValue = localStorage.getItem(storageKeys.apiKey);
        if (!legacyValue) {
            return;
        }

        sessionStorage.setItem(storageKeys.apiKey, legacyValue);
        localStorage.removeItem(storageKeys.apiKey);
    }

    function bindEvents() {
        elements.connectButton.addEventListener("click", async () => {
            state.apiKey = elements.apiKeyInput.value.trim();
            if (state.apiKey) {
                sessionStorage.setItem(storageKeys.apiKey, state.apiKey);
            } else {
                sessionStorage.removeItem(storageKeys.apiKey);
            }

            await connect();
        });

        elements.includeHiddenToggle.addEventListener("change", async (event) => {
            state.includeHidden = event.target.checked;
            await loadDirectory(state.currentPath, { preserveSelection: true });
        });

        elements.themeToggle.addEventListener("click", () => {
            state.theme = state.theme === "dark" ? "light" : "dark";
            document.body.dataset.theme = state.theme;
            localStorage.setItem(storageKeys.theme, state.theme);
            elements.themeToggle.textContent = state.theme === "dark" ? "Light Mode" : "Dark Mode";
        });

        elements.backButton.addEventListener("click", async () => {
            if (state.backStack.length === 0) {
                if (state.currentPath !== "/") {
                    await loadDirectory(getParentPath(state.currentPath));
                }
                return;
            }

            state.forwardStack.push(state.currentPath);
            const previousPath = state.backStack.pop();
            await loadDirectory(previousPath, { fromHistory: true });
        });

        elements.upButton.addEventListener("click", async () => {
            if (state.currentPath === "/") {
                showToast("Already at root.");
                return;
            }

            await navigateTo(getParentPath(state.currentPath));
        });

        elements.refreshButton.addEventListener("click", async () => {
            await refreshSelection();
        });

        elements.newFolderButton.addEventListener("click", async () => {
            const folderName = window.prompt("New folder name");
            if (!folderName) {
                return;
            }

            await createDirectory(folderName.trim());
        });

        elements.uploadButton.addEventListener("click", () => {
            elements.fileInput.click();
        });

        elements.fileInput.addEventListener("change", async (event) => {
            const files = Array.from(event.target.files || []);
            if (files.length === 0) {
                return;
            }

            await uploadFiles(files);
            event.target.value = "";
        });

        elements.downloadButton.addEventListener("click", async () => {
            if (!state.selectedEntry || state.selectedEntry.entryType !== "FILE") {
                return;
            }

            await downloadEntry(state.selectedEntry);
        });

        elements.deleteButton.addEventListener("click", async () => {
            if (!state.selectedEntry) {
                return;
            }

            const approved = window.confirm("Delete '" + state.selectedEntry.name + "'?");
            if (!approved) {
                return;
            }

            await deleteEntry(state.selectedEntry.relativePath);
        });

        elements.previewButton.addEventListener("click", async () => {
            if (state.selectedEntry) {
                await renderPreview(state.selectedEntry, false);
            }
        });

        elements.streamButton.addEventListener("click", async () => {
            if (state.selectedEntry) {
                await renderPreview(state.selectedEntry, true);
            }
        });

        elements.detailButton.addEventListener("click", async () => {
            if (state.selectedEntry) {
                await loadDetail(state.selectedEntry.relativePath);
            }
        });

        elements.applyTagsButton.addEventListener("click", async () => {
            await applyTags();
        });

        elements.removeTagsButton.addEventListener("click", async () => {
            await removeTags();
        });

        ["dragenter", "dragover"].forEach((eventName) => {
            elements.dropZone.addEventListener(eventName, (event) => {
                event.preventDefault();
                elements.dropZone.classList.add("active");
            });
        });

        ["dragleave", "dragend", "drop"].forEach((eventName) => {
            elements.dropZone.addEventListener(eventName, (event) => {
                event.preventDefault();
                elements.dropZone.classList.remove("active");
            });
        });

        elements.dropZone.addEventListener("drop", async (event) => {
            const files = Array.from(event.dataTransfer?.files || []);
            if (files.length === 0) {
                return;
            }

            await uploadFiles(files);
        });
    }

    async function connect() {
        await Promise.all([
            loadAllTags(),
            loadDirectory("/", { resetHistory: true })
        ]);
    }

    async function loadAllTags() {
        const response = await apiFetch("/api/v1/tags");
        state.availableTags = response.tags || [];
        updateTagControls();
    }

    async function navigateTo(path) {
        state.backStack.push(state.currentPath);
        state.forwardStack = [];
        await loadDirectory(path);
    }

    async function refreshSelection() {
        const selectedPath = state.selectedEntry?.relativePath || null;
        await loadDirectory(state.currentPath, { preserveSelection: true });

        if (!selectedPath) {
            return;
        }

        const entry = state.currentEntries.find((item) => item.relativePath === selectedPath);
        if (!entry) {
            return;
        }

        state.selectedEntry = entry;
        renderSelection();
        highlightSelectedRow();
        await loadDetail(selectedPath);
    }

    async function loadDirectory(path, options = {}) {
        const normalizedPath = normalizePath(path);
        const previousSelectionPath = options.preserveSelection ? state.selectedEntry?.relativePath : null;

        if (options.resetHistory) {
            state.backStack = [];
            state.forwardStack = [];
        }

        setStatus("Loading folder...");

        const query = new URLSearchParams({
            path: normalizedPath,
            includeHidden: String(state.includeHidden)
        });

        const response = await apiFetch("/api/v1/entries?" + query.toString());
        state.currentPath = response.currentPath;
        state.currentEntries = response.entries || [];
        state.selectedEntry = previousSelectionPath
            ? state.currentEntries.find((entry) => entry.relativePath === previousSelectionPath) || null
            : null;
        state.selectedDetail = null;

        renderBreadcrumbs();
        renderTable();
        renderSelection();
        updateNavigationButtons();

        if (state.selectedEntry) {
            await loadDetail(state.selectedEntry.relativePath);
        } else {
            renderDetail(null);
            clearPreview();
        }

        setStatus("Ready");
    }

    async function loadDetail(path) {
        const query = new URLSearchParams({ path });
        const detail = await apiFetch("/api/v1/entries/detail?" + query.toString());
        state.selectedDetail = detail;
        renderDetail(detail);
        updateTagControls();
        return detail;
    }

    async function createDirectory(name) {
        setStatus("Creating folder...");
        await apiFetch("/api/v1/directories", {
            method: "POST",
            body: JSON.stringify({
                parentPath: state.currentPath,
                name
            })
        });
        showToast("Folder created.");
        await loadDirectory(state.currentPath);
    }

    async function uploadFiles(files) {
        setStatus("Uploading...");
        let uploaded = 0;

        for (const file of files) {
            const formData = new FormData();
            formData.append("parentPath", state.currentPath);
            formData.append("conflictPolicy", elements.conflictPolicySelect.value);
            formData.append("file", file);

            await apiFetch("/api/v1/files/upload", {
                method: "POST",
                body: formData,
                isFormData: true
            });

            uploaded += 1;
        }

        showToast(uploaded + " file(s) uploaded.");
        await loadDirectory(state.currentPath);
    }

    async function deleteEntry(path) {
        setStatus("Deleting...");
        await apiFetch("/api/v1/entries", {
            method: "DELETE",
            body: JSON.stringify({ path })
        });
        showToast("Entry deleted.");
        state.selectedEntry = null;
        state.selectedDetail = null;
        await loadDirectory(state.currentPath);
    }

    async function downloadEntry(entry) {
        setStatus("Preparing download...");
        const query = new URLSearchParams({ path: entry.relativePath });
        const blob = await apiFetch("/api/v1/files/download?" + query.toString(), {}, "blob");
        const url = URL.createObjectURL(blob);

        try {
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = entry.name;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            showToast("Download started.");
        } finally {
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
            setStatus("Ready");
        }
    }

    async function applyTags() {
        if (!state.selectedEntry || state.selectedEntry.entryType !== "FILE") {
            return;
        }

        const selectedIds = Array.from(elements.existingTagsSelect.selectedOptions)
            .map((option) => Number(option.value))
            .filter((value) => Number.isFinite(value) && value > 0);
        const newNames = splitTagInput(elements.newTagsInput.value);

        if (selectedIds.length === 0 && newNames.length === 0) {
            showToast("Select an existing tag or enter a new tag.");
            return;
        }

        setStatus("Applying tags...");
        await apiFetch("/api/v1/files/tags", {
            method: "POST",
            body: JSON.stringify({
                path: state.selectedEntry.relativePath,
                tagIds: selectedIds,
                tagNames: newNames
            })
        });

        elements.newTagsInput.value = "";
        await loadAllTags();
        await loadDirectory(state.currentPath, { preserveSelection: true });

        if (state.selectedEntry) {
            await loadDetail(state.selectedEntry.relativePath);
        }

        showToast("Tags applied.");
    }

    async function removeTags() {
        if (!state.selectedEntry || state.selectedEntry.entryType !== "FILE") {
            return;
        }

        const tagIds = Array.from(elements.attachedTagsSelect.selectedOptions)
            .map((option) => Number(option.value))
            .filter((value) => Number.isFinite(value) && value > 0);

        if (tagIds.length === 0) {
            showToast("Select one or more attached tags to remove.");
            return;
        }

        setStatus("Removing tags...");
        await apiFetch("/api/v1/files/tags", {
            method: "DELETE",
            body: JSON.stringify({
                path: state.selectedEntry.relativePath,
                tagIds
            })
        });

        await loadDirectory(state.currentPath, { preserveSelection: true });

        if (state.selectedEntry) {
            await loadDetail(state.selectedEntry.relativePath);
        }

        showToast("Tags removed.");
    }

    async function renderPreview(entry, useStreamEndpoint) {
        clearPreview();

        if (!entry) {
            return;
        }

        if (entry.entryType === "DIRECTORY") {
            const detail = state.selectedDetail || await loadDetail(entry.relativePath);
            renderDirectoryPreview(detail);
            return;
        }

        const previewType = getPreviewType(entry);
        if (previewType === "none") {
            renderUnsupportedPreview(entry);
            return;
        }

        const requestId = ++state.previewRequestId;
        const endpoint = useStreamEndpoint ? "/api/v1/files/stream" : "/api/v1/files/download";
        const query = new URLSearchParams({ path: entry.relativePath });

        setStatus(useStreamEndpoint ? "Loading stream..." : "Loading preview...");

        if (previewType === "text") {
            const text = await apiFetch(endpoint + "?" + query.toString(), {}, "text");
            if (requestId !== state.previewRequestId) {
                return;
            }

            elements.previewKindLabel.textContent = buildPreviewLabel(previewType, useStreamEndpoint);
            elements.previewContent.innerHTML = '<pre class="preview-text"></pre>';
            elements.previewContent.querySelector(".preview-text").textContent = text;
            setStatus("Ready");
            return;
        }

        const blob = await apiFetch(endpoint + "?" + query.toString(), {}, "blob");
        if (requestId !== state.previewRequestId) {
            return;
        }

        state.previewUrl = URL.createObjectURL(blob);
        elements.previewKindLabel.textContent = buildPreviewLabel(previewType, useStreamEndpoint);

        if (previewType === "image") {
            elements.previewContent.innerHTML = '<img class="preview-viewer preview-image" alt="">';
            const image = elements.previewContent.querySelector("img");
            image.src = state.previewUrl;
            image.alt = entry.name;
        } else if (previewType === "video") {
            elements.previewContent.innerHTML = '<video class="preview-viewer preview-video" controls></video>';
            elements.previewContent.querySelector("video").src = state.previewUrl;
        } else if (previewType === "audio") {
            elements.previewContent.innerHTML = '<audio class="preview-viewer preview-audio" controls></audio>';
            elements.previewContent.querySelector("audio").src = state.previewUrl;
        } else if (previewType === "pdf") {
            elements.previewContent.innerHTML = '<iframe class="preview-viewer preview-frame" title="PDF preview"></iframe>';
            elements.previewContent.querySelector("iframe").src = state.previewUrl;
        }

        setStatus("Ready");
    }

    function renderDirectoryPreview(detail) {
        elements.previewKindLabel.textContent = "Directory";
        elements.previewContent.innerHTML =
            '<div class="empty-preview"><h3>Folder selected</h3><p>Double click the row or click the same folder twice to enter it.</p></div>';
        renderDetail(detail);
    }

    function renderUnsupportedPreview(entry) {
        elements.previewKindLabel.textContent = "Unsupported";
        elements.previewContent.innerHTML =
            '<div class="empty-preview"><h3>No quick viewer for this file</h3><p>Download the file or extend the server with a dedicated preview pipeline.</p></div>';
        showToast("No preview support for '" + entry.name + "'.");
    }

    function buildPreviewLabel(previewType, useStreamEndpoint) {
        const mode = useStreamEndpoint ? "Stream" : "Preview";
        return previewType.charAt(0).toUpperCase() + previewType.slice(1) + " " + mode;
    }

    function renderTable() {
        elements.entryTableBody.innerHTML = "";
        elements.entryCountLabel.textContent = state.currentEntries.length + " item(s)";
        elements.currentPathLabel.textContent = state.currentPath;
        elements.emptyState.hidden = state.currentEntries.length > 0;

        for (const entry of state.currentEntries) {
            const row = document.createElement("tr");
            row.className = "entry-row";
            row.dataset.path = entry.relativePath;

            const tagsMarkup = renderTagMarkup(entry.tags || []);
            const typeLabel = entry.entryType === "DIRECTORY" ? "Folder" : describeFileType(entry);
            row.innerHTML = [
                '<td><div class="entry-name"><span class="entry-badge ' + (entry.entryType === "DIRECTORY" ? "dir" : "file") + '">',
                entry.entryType === "DIRECTORY" ? "DIR" : "FILE",
                '</span><div><div>',
                escapeHtml(entry.name),
                '</div>',
                tagsMarkup ? '<div class="entry-meta">' + tagsMarkup + "</div>" : "",
                '</div></div></td>',
                "<td>" + escapeHtml(typeLabel) + "</td>",
                "<td>" + escapeHtml(formatSize(entry.sizeBytes)) + "</td>",
                "<td>" + escapeHtml(formatDate(entry.modifiedAt)) + "</td>"
            ].join("");

            row.addEventListener("click", async () => {
                const wasSelected = state.selectedEntry?.relativePath === entry.relativePath;
                state.selectedEntry = entry;
                state.selectedDetail = null;
                renderSelection();
                renderDetail(null);
                highlightSelectedRow();

                const detail = await loadDetail(entry.relativePath);
                if (entry.entryType === "DIRECTORY") {
                    renderDirectoryPreview(detail);
                    if (wasSelected) {
                        await navigateTo(entry.relativePath);
                    }
                    return;
                }

                await renderPreview(entry, false);
            });

            row.addEventListener("dblclick", async () => {
                if (entry.entryType === "DIRECTORY") {
                    await navigateTo(entry.relativePath);
                    return;
                }

                await renderPreview(entry, true);
            });

            elements.entryTableBody.appendChild(row);
        }

        highlightSelectedRow();
    }

    function renderSelection() {
        const entry = state.selectedEntry;
        if (!entry) {
            elements.selectionSummary.textContent = "No selection";
            elements.downloadButton.disabled = true;
            elements.deleteButton.disabled = true;
            elements.previewButton.disabled = true;
            elements.streamButton.disabled = true;
            elements.detailButton.disabled = true;
            updateTagControls();
            return;
        }

        const previewType = getPreviewType(entry);
        const previewEnabled = previewType !== "none" || entry.entryType === "DIRECTORY";

        elements.selectionSummary.textContent = entry.name + " / " + (entry.entryType === "DIRECTORY" ? "Folder" : "File");
        elements.downloadButton.disabled = entry.entryType !== "FILE";
        elements.deleteButton.disabled = false;
        elements.previewButton.disabled = !previewEnabled;
        elements.streamButton.disabled = entry.entryType !== "FILE" || previewType === "none";
        elements.detailButton.disabled = false;
        updateTagControls();
    }

    function renderBreadcrumbs() {
        elements.breadcrumbs.innerHTML = "";
        const segments = state.currentPath === "/" ? [] : state.currentPath.slice(1).split("/");
        const crumbs = [{ label: "root", path: "/" }];

        let cursor = "";
        for (const segment of segments) {
            cursor += "/" + segment;
            crumbs.push({ label: segment, path: cursor });
        }

        for (const crumb of crumbs) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "breadcrumb";
            button.textContent = crumb.label;
            button.addEventListener("click", async () => {
                if (crumb.path !== state.currentPath) {
                    await navigateTo(crumb.path);
                }
            });
            elements.breadcrumbs.appendChild(button);
        }
    }

    function renderDetail(detail) {
        const values = detail ? [
            detail.name || "-",
            detail.relativePath || "-",
            detail.entryType || "-",
            detail.mimeType || "-",
            formatSize(detail.sizeBytes),
            formatDate(detail.modifiedAt),
            formatDate(detail.createdAtFs),
            detail.hidden ? "Yes" : "No",
            detail.fileId != null ? String(detail.fileId) : "-"
        ] : ["-", "-", "-", "-", "-", "-", "-", "-", "-"];

        const items = Array.from(elements.detailList.querySelectorAll("dd"));
        values.forEach((value, index) => {
            items[index].textContent = value;
        });

        renderSelectedTags(detail && detail.tags ? detail.tags : []);
    }

    function renderSelectedTags(tags) {
        if (!tags || tags.length === 0) {
            elements.selectedTagList.innerHTML = '<span class="tag-chip tag-chip-muted">No tags</span>';
            elements.attachedTagsSelect.innerHTML = "";
            return;
        }

        const sortedTags = tags
            .slice()
            .sort((left, right) => left.tagName.localeCompare(right.tagName));

        elements.selectedTagList.innerHTML = sortedTags
            .map((tag) => '<span class="tag-chip">' + escapeHtml(tag.tagName) + "</span>")
            .join("");
    }

    function updateTagControls() {
        const selectedFile = state.selectedEntry && state.selectedEntry.entryType === "FILE";
        const existingTags = state.selectedDetail?.tags || [];
        const existingTagIds = new Set(existingTags.map((tag) => tag.tagId));
        const hasAttachedTags = existingTags.length > 0;

        elements.applyTagsButton.disabled = !selectedFile;
        elements.removeTagsButton.disabled = !selectedFile || !hasAttachedTags;
        elements.newTagsInput.disabled = !selectedFile;
        elements.existingTagsSelect.disabled = !selectedFile;
        elements.attachedTagsSelect.disabled = !selectedFile || !hasAttachedTags;
        elements.attachedTagsSelect.innerHTML = "";
        elements.existingTagsSelect.innerHTML = "";

        if (!selectedFile) {
            elements.tagStatusLabel.textContent = "Select a file";
            return;
        }

        elements.tagStatusLabel.textContent = existingTags.length + " tag(s) attached";

        for (const tag of state.availableTags) {
            const option = document.createElement("option");
            option.value = String(tag.tagId);
            option.textContent = tag.tagName;
            option.disabled = existingTagIds.has(tag.tagId);
            elements.existingTagsSelect.appendChild(option);
        }

        for (const tag of existingTags.slice().sort((left, right) => left.tagName.localeCompare(right.tagName))) {
            const option = document.createElement("option");
            option.value = String(tag.tagId);
            option.textContent = tag.tagName;
            elements.attachedTagsSelect.appendChild(option);
        }
    }

    function renderUnsupportedState(message) {
        clearPreview();
        elements.previewKindLabel.textContent = "Idle";
        elements.previewContent.innerHTML =
            '<div class="empty-preview"><h3>' + escapeHtml(message) + "</h3><p>Connect first to browse files.</p></div>";
    }

    function highlightSelectedRow() {
        const rows = elements.entryTableBody.querySelectorAll(".entry-row");
        rows.forEach((row) => {
            row.classList.toggle("selected", row.dataset.path === state.selectedEntry?.relativePath);
        });
    }

    function clearPreview() {
        if (state.previewUrl) {
            URL.revokeObjectURL(state.previewUrl);
            state.previewUrl = null;
        }

        elements.previewKindLabel.textContent = "Idle";
        elements.previewContent.innerHTML =
            '<div class="empty-preview"><h3>Preview appears here</h3><p>Select a file to preview it.</p></div>';
    }

    function updateNavigationButtons() {
        elements.backButton.disabled = state.backStack.length === 0 && state.currentPath === "/";
        elements.upButton.disabled = state.currentPath === "/";
    }

    function getPreviewType(entry) {
        if (!entry || entry.entryType !== "FILE") {
            return "none";
        }

        const extension = (entry.extension || "").toLowerCase();
        const mimeType = (entry.mimeType || "").toLowerCase();

        if (mimeType.startsWith("image/")) {
            return "image";
        }

        if (mimeType.startsWith("video/")) {
            return "video";
        }

        if (mimeType.startsWith("audio/")) {
            return "audio";
        }

        if (mimeType === "application/pdf" || extension === "pdf") {
            return "pdf";
        }

        if (mimeType.startsWith("text/") || previewableTextExtensions.has(extension) || mimeType.includes("json") || mimeType.includes("xml")) {
            return "text";
        }

        return "none";
    }

    function getParentPath(path) {
        const normalized = normalizePath(path);
        if (normalized === "/") {
            return "/";
        }

        const segments = normalized.slice(1).split("/");
        segments.pop();
        return segments.length === 0 ? "/" : "/" + segments.join("/");
    }

    function normalizePath(path) {
        if (!path || path.trim() === "") {
            return "/";
        }

        const normalized = path.replace(/\\/g, "/").trim();
        if (normalized === "/") {
            return "/";
        }

        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    function splitTagInput(rawValue) {
        return rawValue
            .split(/[\n,]/)
            .map((value) => value.trim())
            .filter(Boolean);
    }

    async function apiFetch(url, options = {}, responseType = "json") {
        if (!state.apiKey) {
            renderUnsupportedState("API Key is required.");
            throw new Error("API Key is required.");
        }

        const headers = new Headers(options.headers || {});
        headers.set(apiKeyHeaderName, state.apiKey);

        if (!options.isFormData && options.body && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }

        const response = await fetch(url, {
            method: options.method || "GET",
            headers,
            body: options.body
        });

        if (!response.ok) {
            let message = "Request failed.";

            try {
                const errorBody = await response.json();
                message = errorBody.message || message;
            } catch (error) {
                if (response.status === 401) {
                    message = "Check API Key.";
                }
            }

            setStatus("Error");
            showToast(message);
            throw new Error(message);
        }

        if (responseType === "blob") {
            return response.blob();
        }

        if (responseType === "text") {
            return response.text();
        }

        if (response.status === 204) {
            return null;
        }

        return response.json();
    }

    function renderTagMarkup(tags) {
        if (!tags || tags.length === 0) {
            return "";
        }

        return tags
            .slice(0, 3)
            .map((tag) => '<span class="tag-chip tag-chip-small">' + escapeHtml(tag.tagName) + "</span>")
            .join("");
    }

    function describeFileType(entry) {
        if (entry.mimeType) {
            return entry.mimeType;
        }

        if (entry.extension) {
            return entry.extension.toUpperCase() + " file";
        }

        return "Unknown";
    }

    function formatSize(sizeBytes) {
        if (sizeBytes == null) {
            return "-";
        }

        const units = ["B", "KB", "MB", "GB", "TB"];
        let value = sizeBytes;
        let unitIndex = 0;

        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex += 1;
        }

        return value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1) + " " + units[unitIndex];
    }

    function formatDate(value) {
        if (!value) {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return new Intl.DateTimeFormat("en-US", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        }).format(date);
    }

    function setStatus(message) {
        elements.statusLabel.textContent = message;
    }

    function showToast(message) {
        elements.toast.textContent = message;
        elements.toast.hidden = false;
        window.clearTimeout(showToast.timeoutId);
        showToast.timeoutId = window.setTimeout(() => {
            elements.toast.hidden = true;
        }, 2800);
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function handleError(error) {
        console.error(error);
        if (error && error.message) {
            showToast(error.message);
        }
    }
})();
