export function createLoadingController(element) {
    let pendingCount = 0;

    return {
        start() {
            pendingCount += 1;
            element.style.opacity = "1";
            element.style.width = "72%";
        },
        end() {
            pendingCount = Math.max(0, pendingCount - 1);
            if (pendingCount !== 0) {
                return;
            }

            element.style.width = "100%";
            window.setTimeout(() => {
                element.style.width = "0";
                element.style.opacity = "0";
            }, 240);
        }
    };
}

export function createToastManager(container) {
    return {
        show(message, type = "info", timeoutMs = 3200) {
            if (!message) {
                return;
            }

            const toast = document.createElement("div");
            toast.className = `toast ${type}`;
            toast.textContent = message;
            container.appendChild(toast);

            window.setTimeout(() => {
                toast.remove();
            }, timeoutMs);
        }
    };
}
