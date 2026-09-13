(() => {
    const form = document.getElementById("loginForm");
    const button = document.getElementById("signInButton");
    const status = document.getElementById("loginStatus");
    let busy = false;
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (busy) return;
        busy = true; button.disabled = true; status.textContent = "Signing in…";
        try {
            const sessionResponse = await fetch("/auth/session", {cache: "no-store"});
            if (!sessionResponse.ok) throw new Error("Could not contact the server. Please try again.");
            const session = await sessionResponse.json();
            const response = await fetch("/auth/login", {
                method: "POST",
                headers: {"Content-Type": "application/json", "X-CSRF-Token": session.csrfToken},
                body: JSON.stringify({username: form.username.value, password: form.password.value})
            });
            const result = await response.json();
            if (!response.ok) throw new Error(result.error || "Could not sign in.");
            form.password.value = "";
            location.replace("/");
        } catch (error) {
            status.textContent = error.message;
            form.password.value = ""; form.password.focus();
        } finally { busy = false; button.disabled = false; }
    });
})();
