const previewableTextExtensions = new Set([
    "txt", "md", "markdown", "json", "xml", "html", "css", "js", "ts", "tsx", "jsx",
    "java", "kt", "yml", "yaml", "properties", "sql", "csv", "log", "conf", "env"
]);

export const previewLimits = {
    text: 500 * 1024,
    image: 20 * 1024 * 1024,
    media: 200 * 1024 * 1024,
    pdf: 50 * 1024 * 1024
};

export function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

export function formatSize(sizeBytes) {
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

export function formatDate(value) {
    if (!value) {
        return "-";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
}

export function normalizePath(path) {
    if (!path || path.trim() === "") {
        return "/";
    }

    const normalized = path.replace(/\\/g, "/").trim();
    if (normalized === "/") {
        return "/";
    }

    return normalized.startsWith("/") ? normalized : "/" + normalized;
}

export function getParentPath(path) {
    const normalizedPath = normalizePath(path);
    if (normalizedPath === "/") {
        return "/";
    }

    const segments = normalizedPath.slice(1).split("/");
    segments.pop();
    return segments.length === 0 ? "/" : "/" + segments.join("/");
}

export function splitTagInput(rawValue) {
    return rawValue
        .split(/[\n,]/)
        .map((value) => value.trim())
        .filter(Boolean);
}

export function buildBreadcrumbs(path) {
    const normalizedPath = normalizePath(path);
    const segments = normalizedPath === "/" ? [] : normalizedPath.slice(1).split("/");
    const crumbs = [{ label: "/", path: "/" }];

    let cursor = "";
    segments.forEach((segment) => {
        cursor += "/" + segment;
        crumbs.push({ label: segment, path: cursor });
    });

    return crumbs;
}

export function getPreviewType(entry) {
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

export function describeEntryKind(entry) {
    if (!entry) {
        return "No selection";
    }

    if (entry.entryType === "DIRECTORY") {
        return "Directory";
    }

    if (entry.mimeType) {
        return entry.mimeType;
    }

    if (entry.extension) {
        return entry.extension.toUpperCase();
    }

    return "File";
}
