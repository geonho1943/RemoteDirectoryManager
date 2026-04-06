const KEYS = {
    apiKey: "rdm.apiKey",
    includeHidden: "rdm.includeHidden",
    lastPath: "rdm.lastPath",
    theme: "rdm.theme"
};

function readBoolean(rawValue, fallbackValue) {
    if (rawValue == null) {
        return fallbackValue;
    }

    return rawValue === "true";
}

export function getApiKey() {
    return sessionStorage.getItem(KEYS.apiKey) || "";
}

export function setApiKey(value) {
    if (!value) {
        sessionStorage.removeItem(KEYS.apiKey);
        return;
    }

    sessionStorage.setItem(KEYS.apiKey, value);
}

export function clearApiKey() {
    sessionStorage.removeItem(KEYS.apiKey);
}

export function getIncludeHiddenPreference() {
    return readBoolean(localStorage.getItem(KEYS.includeHidden), true);
}

export function setIncludeHiddenPreference(value) {
    localStorage.setItem(KEYS.includeHidden, String(Boolean(value)));
}

export function getLastPath() {
    return sessionStorage.getItem(KEYS.lastPath) || "/";
}

export function setLastPath(path) {
    sessionStorage.setItem(KEYS.lastPath, path || "/");
}

export function clearLastPath() {
    sessionStorage.removeItem(KEYS.lastPath);
}

export function getThemePreference() {
    return localStorage.getItem(KEYS.theme) || "dark";
}

export function setThemePreference(value) {
    localStorage.setItem(KEYS.theme, value || "dark");
}
