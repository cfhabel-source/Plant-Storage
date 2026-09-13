(() => {
    const nativeFetch = window.fetch.bind(window);
    let sessionPromise;
    function session() {
        if (!sessionPromise) sessionPromise = nativeFetch("/auth/session", {cache:"no-store"})
            .then(async response => {
                if (!response.ok) throw new Error("Could not check your login. Refresh the page.");
                const data = await response.json();
                if (!data.authenticated) { location.replace("/login.html"); throw new Error("Please sign in again."); }
                return data;
            }).catch(error => { sessionPromise = undefined; throw error; });
        return sessionPromise;
    }
    // A shared helper protects the existing fetch calls, including multipart uploads.
    window.fetch = async (input, init = {}) => {
        const url = new URL(input instanceof Request ? input.url : input, location.href);
        if (url.origin !== location.origin) return nativeFetch(input, init);
        const method = (init.method || (input instanceof Request ? input.method : "GET")).toUpperCase();
        const options = {...init};
        if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
            const data = await session();
            const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
            headers.set("X-CSRF-Token", data.csrfToken);
            options.headers = headers;
        }
        const response = await nativeFetch(input, options);
        if (response.status === 401) {
            sessionPromise = undefined; location.replace("/login.html");
            throw new Error("Your session has expired. Please sign in again.");
        }
        return response;
    };
    const logout = document.getElementById("logoutButton");
    const status = document.getElementById("authStatus");
    logout.addEventListener("click", async () => {
        logout.disabled = true;
        try {
            const response = await fetch("/auth/logout", {method:"POST"});
            if (!response.ok) throw new Error("Could not sign out. Refresh and try again.");
            document.getElementById("plantsContainer").replaceChildren();
            sessionPromise = undefined; location.replace("/login.html");
        } catch (error) { status.textContent = error.message; logout.disabled = false; }
    });
    session().catch(error => { status.textContent = error.message; });
    // Revalidate after browser back/forward restoration; don't reveal an old cached page.
    window.addEventListener("pageshow", event => {
        if (event.persisted) { sessionPromise = undefined; location.reload(); }
    });
})();
