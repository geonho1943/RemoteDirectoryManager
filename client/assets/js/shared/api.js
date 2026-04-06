import { getApiKey } from "./storage.js";

function createApiError(status, message) {
    const error = new Error(message);
    error.status = status;
    return error;
}

export async function apiRequest(path, options = {}) {
    const {
        method = "GET",
        headers = {},
        apiKey = getApiKey(),
        json,
        body,
        responseType = "json"
    } = options;

    const requestHeaders = new Headers(headers);
    if (apiKey) {
        requestHeaders.set("API-Key", apiKey);
    }

    let requestBody = body;
    if (json !== undefined) {
        requestHeaders.set("Content-Type", "application/json");
        requestBody = JSON.stringify(json);
    }

    const response = await fetch(`/api/v1${path}`, {
        method,
        headers: requestHeaders,
        body: requestBody
    });

    if (!response.ok) {
        let message = `HTTP ${response.status}`;

        try {
            const payload = await response.json();
            message = payload.message || message;
        } catch (jsonError) {
            const text = await response.text().catch(() => "");
            if (text) {
                message = text;
            }
        }

        throw createApiError(response.status, message);
    }

    if (responseType === "blob") {
        return response.blob();
    }

    if (responseType === "text") {
        return response.text();
    }

    if (responseType === "response") {
        return response;
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

export async function checkHealth() {
    const response = await fetch("/api/v1/health");
    if (!response.ok) {
        throw createApiError(response.status, `HTTP ${response.status}`);
    }

    return response.json();
}
