import { apiRequest, checkHealth } from "../shared/api.js";
import { clearApiKey, getApiKey, setApiKey } from "../shared/storage.js";
import { createLoadingController, createToastManager } from "../shared/ui.js";

const elements = {
    loginForm: document.getElementById("loginForm"),
    apiKeyInput: document.getElementById("apiKeyInput"),
    connectButton: document.getElementById("connectButton"),
    loginError: document.getElementById("loginError"),
    loadingBar: document.getElementById("loading-bar"),
    toastContainer: document.getElementById("toast-container")
};

const loading = createLoadingController(elements.loadingBar);
const toast = createToastManager(elements.toastContainer);

function setError(message) {
    if (!message) {
        elements.loginError.textContent = "";
        elements.loginError.classList.add("hidden");
        return;
    }

    elements.loginError.textContent = message;
    elements.loginError.classList.remove("hidden");
}

async function connect() {
    const apiKey = elements.apiKeyInput.value.trim();
    if (!apiKey) {
        setError("Enter an API key first.");
        elements.apiKeyInput.focus();
        return;
    }

    setError("");
    elements.connectButton.disabled = true;
    loading.start();

    try {
        await checkHealth();
        await apiRequest(`/entries?path=${encodeURIComponent("/")}&includeHidden=true`, {
            apiKey
        });

        setApiKey(apiKey);
        toast.show("Connected.", "success");
        window.location.href = "./explorer.html";
    } catch (error) {
        clearApiKey();
        const message = error.status === 401 ? "Check the API key." : error.message;
        setError(message);
    } finally {
        elements.connectButton.disabled = false;
        loading.end();
    }
}

function init() {
    const existingApiKey = getApiKey();
    if (existingApiKey) {
        window.location.href = "./explorer.html";
        return;
    }

    elements.loginForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        await connect();
    });
}

init();
