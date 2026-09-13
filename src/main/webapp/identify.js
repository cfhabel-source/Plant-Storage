(() => {
    "use strict";
    const root = document.getElementById("plantIdentifier");
    if (!root) return;
    let history = [];
    let generation = 0;
    let controller;

    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        if (className) node.className = className;
        return node;
    }
    function button(label, action, secondary = false) {
        const node = element("button", label);
        node.type = "button";
        if (secondary) node.className = "identify-secondary";
        node.addEventListener("click", action);
        return node;
    }

    async function load(proposed = history) {
        controller?.abort();
        controller = new AbortController();
        const currentGeneration = ++generation;
        root.setAttribute("aria-busy", "true");
        root.replaceChildren(element("p", "Loading the next question…"));
        try {
            const response = await fetch(`/identify/?answers=${encodeURIComponent(JSON.stringify(proposed))}`, {
                signal: controller.signal
            });
            if (!response.ok) throw new Error(`Identification unavailable (HTTP ${response.status}).`);
            const result = await response.json();
            if (currentGeneration !== generation) return;
            history = proposed.slice();
            render(result);
        } catch (error) {
            if (error.name === "AbortError" || currentGeneration !== generation) return;
            root.replaceChildren(element("p", error.message + " Check that the server is running."));
            root.append(button("Try again", () => load(proposed)), button("Restart", () => load([]), true));
        } finally {
            if (currentGeneration === generation) root.setAttribute("aria-busy", "false");
        }
    }

    function render(result) {
        root.replaceChildren();
        root.append(element("p", `${result.candidates.length} of 32 entries remain`, "identify-count"));
        const title = element("h3", result.finished ? "Possible matches" : result.question.text);
        title.tabIndex = -1;
        root.append(title, element("p", result.message));

        if (!result.finished) {
            root.append(element("p", result.question.help, "identify-help"));
            const choices = element("div", undefined, "identify-choices");
            for (const option of result.question.options) {
                choices.append(button(option.label, () => load([
                    ...history,
                    {questionId: result.question.id, optionId: option.id}
                ]), option.id === "unknown"));
            }
            root.append(choices);
        } else {
            const list = element("div", undefined, "identify-results");
            for (const plant of result.candidates) {
                const card = element("article", undefined, "identify-candidate");
                card.append(element("h4", plant.name));
                if (plant.originalName !== plant.name)
                    card.append(element("p", `Your list: ${plant.originalName}`, "identify-help"));
                card.append(element("p", plant.note));
                const link = element("a", "Compare botanical source");
                link.href = plant.source;
                link.target = "_blank";
                link.rel = "noopener noreferrer";
                card.append(link);
                // No confident selection is offered when there is no foliage.
                if (!history.some(a => a.questionId === "foliage" && a.optionId === "no")) {
                    card.append(button("Use this plant", () => {
                        const species = document.getElementById("species");
                        if (!species) return;
                        if (species.value.trim() && species.value !== plant.name &&
                            !window.confirm("Replace the species currently entered in the plant form?")) return;
                        species.value = plant.name;
                        species.dispatchEvent(new Event("input", {bubbles: true}));
                        species.focus();
                        species.scrollIntoView({behavior: "smooth", block: "center"});
                        document.getElementById("identifySelectionStatus").textContent =
                            `${plant.name} copied to the species field. Review it, then submit the plant form to save.`;
                    }));
                }
                list.append(card);
            }
            root.append(list);
        }
        const navigation = element("div", undefined, "identify-navigation");
        const back = button("Back", () => load(history.slice(0, -1)), true);
        back.disabled = history.length === 0;
        navigation.append(back, button("Restart", () => load([]), true));
        root.append(navigation);
        if (history.length) title.focus();
    }

    // Load only when opened so plant storage works even if this endpoint fails.
    const panel = document.getElementById("identifyPanel");
    let started = false;
    panel.addEventListener("toggle", () => {
        if (panel.open && !started) { started = true; load([]); }
    });
})();
