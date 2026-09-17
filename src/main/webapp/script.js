console.log("script.js loaded");

// -------------------------------
// CREATE / UPDATE PLANT
// -------------------------------
document.getElementById("plantForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const plantForm = document.getElementById("plantForm");
    const editId = plantForm.dataset.editId;

    const plant = {
        name: document.getElementById("name").value,
        species: document.getElementById("species").value,
        location: document.getElementById("location").value,
        acquiredAt: document.getElementById("acquiredAt").value,
        careInstructions: document.getElementById("careInstructions").value,
        notes: document.getElementById("notes").value
    };

    try {
        let response;

        if (editId) {
            response = await fetch(`/plants/?id=${editId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(plant)
            });


        } else {
            response = await fetch("/plants/", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(plant)
            });
        }

        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.error || "Could not save plant");
        }

        plantForm.dataset.editId = "";
        alert(result.status || "Plant saved");
        await loadPlants();

    } catch (err) {
        console.error("FORM SUBMIT ERROR:", err);
        alert(err.message);
    }
});

// -------------------------------
// LOAD ALL PLANTS
// -------------------------------
let allPlants = [];
let plantsLoaded = false;
let pendingLoad = null;
function searchStatus(message) {
    let status = document.getElementById("searchStatus");
    if (!status) {
        status = document.createElement("p");
        status.id = "searchStatus";
        status.setAttribute("role", "status");
        document.getElementById("plantsContainer").before(status);
    }
    status.textContent = message;
}
async function loadPlants() {
    if (pendingLoad) return pendingLoad;
    searchStatus("Loading plants...");
    pendingLoad = (async () => {
        try {
            const response = await fetch("/plants/");
            if (!response.ok) throw new Error("Could not load plants (" + response.status + ")");
            const plants = await response.json();
            if (!Array.isArray(plants)) throw new Error("Unexpected plant data");
            allPlants = plants.filter(p => p && Number.isSafeInteger(p.id));
            plantsLoaded = true;
            applySearchAndSort();
        } catch (err) {
            console.error("LOAD ALL ERROR:", err);
            searchStatus("Could not refresh plants. Click Load Plants to retry.");
        }
    })();
    try { await pendingLoad; } finally { pendingLoad = null; }
}
function applySearchAndSort() {
    const keyword = document.getElementById("searchInput").value.trim().toLocaleLowerCase("de");
    const fields = ["name", "species", "location", "acquiredAt", "notes", "careInstructions"];
    const terms = keyword.split(/\s+/).filter(Boolean);
    const filtered = allPlants.filter(plant => terms.every(term =>
        fields.some(field => String(plant[field] ?? "").toLocaleLowerCase("de").includes(term))
    ));
    renderPlants(sortPlants(filtered, document.getElementById("sortSelect").value));
    searchStatus(filtered.length ? `${filtered.length} of ${allPlants.length} plants` : "No matching plants.");
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({"&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;"}[char]));
}

function highlight(value, keyword) {
    const text = String(value ?? "");
    if (!keyword) return escapeHtml(text);
    // Escape each text fragment before adding our own highlight markup.
    const needle = keyword.toLocaleLowerCase();
    const haystack = text.toLocaleLowerCase();
    let result = "", offset = 0, index;
    while ((index = haystack.indexOf(needle, offset)) !== -1) {
        result += escapeHtml(text.slice(offset, index));
        result += '<span class="highlight">' + escapeHtml(text.slice(index, index + keyword.length)) + '</span>';
        offset = index + keyword.length;
    }
    return result + escapeHtml(text.slice(offset));
}

function sortPlants(plants, sortKey) {
    return [...plants].sort((a, b) =>
        String(a[sortKey] ?? "").localeCompare(String(b[sortKey] ?? ""), "de", {sensitivity: "base"})
    );
}

// -------------------------------
// RENDER PLANTS
// -------------------------------
function renderPlants(plants) {
    const container = document.getElementById("plantsContainer");
    container.innerHTML = "";

    const keyword = document.getElementById("searchInput").value.trim();

    plants.forEach(plant => {
        if (!plant || !Number.isSafeInteger(plant.id)) return;
        const div = document.createElement("div");
        div.className = "plant-card";

        // ⭐ FIXED: use publicUrl instead of filePath
        const photosHtml = plant.photos?.map(photo => {
            if (!photo || !Number.isSafeInteger(photo.id)) return "";
            return `
                <div class="photo-item">
                    <img
                        src="/photos/?plantId=${plant.id}&id=${photo.id}"
                        class="plant-photo"
                        alt="Plant photo"
                        loading="lazy"
                    >
                    <button
                         type="button"
                         class="delete-photo-btn"
                         data-photo-id="${photo.id}"
                         data-plant-id="${plant.id}"
                     >
                         Delete Photo
                     </button>
                </div>
            `;
        }).join("") || "";

        div.innerHTML = `
            <h3>${highlight(plant.name, keyword)}</h3>
            <p><strong>Species:</strong> ${highlight(plant.species || "Unknown", keyword)}</p>
            <p><strong>Location:</strong> ${highlight(plant.location || "Unknown", keyword)}</p>
            <p><strong>Acquired At:</strong> ${highlight(plant.acquiredAt || "Unknown", keyword)}</p>
            <p><strong>Care Instructions:</strong> ${highlight(plant.careInstructions || "None", keyword)}</p>
            <p><strong>Notes:</strong> ${highlight(plant.notes || "None", keyword)}</p>

            <div class="photo-container">${photosHtml}</div>

            <input type="file" accept="image/*" multiple hidden>
            <button type="button" class="upload-btn" data-id="${plant.id}">
                Upload Photo
            </button>

            <button class="edit-btn" data-id="${plant.id}">Edit</button>
            <button class="delete-btn" data-id="${plant.id}">Delete</button>
        `;

        container.appendChild(div);

        // -------------------------------
        // UPLOAD PHOTOS
        // -------------------------------
        const uploadButton = div.querySelector(".upload-btn");
        const fileInput = div.querySelector('input[type="file"]');

        uploadButton.addEventListener("click", () => {
            fileInput.click();
        });

        fileInput.addEventListener("change", async () => {
            const files = Array.from(fileInput.files);
            if (!files.length) return;

            uploadButton.disabled = true;

            try {
                for (const file of files) {
                    const formData = new FormData();
                    formData.append("photo", file);

                    const response = await fetch(
                        `/plants/?plantId=${encodeURIComponent(plant.id)}`,
                        {
                            method: "PUT",
                            body: formData
                        }
                    );

                    if (!response.ok) {
                        throw new Error(
                            `Upload failed (${response.status}): ${await response.text()}`
                        );
                    }
                }

                alert("Photos uploaded!");
                await loadPlants();
            } catch (err) {
                console.error("UPLOAD ERROR:", err);
                alert(err.message);
            } finally {
                fileInput.value = "";
                uploadButton.disabled = false;
            }
        });

        // -------------------------------
        // DELETE PLANT (FIXED)
        // -------------------------------
        const deleteButton = div.querySelector(".delete-btn");
        deleteButton.addEventListener("click", async () => {
            const plantId = deleteButton.dataset.id;

            if (!confirm("Delete this plant?")) return;

            try {
                await fetch(`/plants/?id=${plantId}`, {
                    method: "DELETE"
                });

                loadPlants();
            } catch (err) {
                console.error("DELETE PLANT ERROR:", err);
            }
        });

        // -------------------------------
        // DELETE PHOTO (FIXED)
        // -------------------------------
        div.querySelectorAll(".delete-photo-btn").forEach(btn => {
            btn.addEventListener("click", async (event) => {
                event.preventDefault();

                const photoId = btn.dataset.photoId;
                const plantId = btn.dataset.plantId;

                if (!confirm("Delete this photo?")) return;

                btn.disabled = true;

                try {
                    const response = await fetch(
                        `/photos/?plantId=${encodeURIComponent(plantId)}&id=${encodeURIComponent(photoId)}`,
                        { method: "DELETE" }
                    );

                    if (!response.ok) {
                        throw new Error(
                            `Delete failed (${response.status}): ${await response.text()}`
                        );
                    }

                    await loadPlants();
                } catch (err) {
                    console.error("DELETE PHOTO ERROR:", err);
                    alert(err.message);
                } finally {
                    btn.disabled = false;
                }
            });
        });

        // -------------------------------
        // EDIT PLANT
        // -------------------------------
        const editButton = div.querySelector(".edit-btn");
        editButton.addEventListener("click", () => {
            document.getElementById("name").value = plant.name;
            document.getElementById("species").value = plant.species;
            document.getElementById("location").value = plant.location;
            document.getElementById("acquiredAt").value = plant.acquiredAt;
            document.getElementById("careInstructions").value = plant.careInstructions;
            document.getElementById("notes").value = plant.notes;

            document.getElementById("plantForm").dataset.editId = plant.id;
        });
    });
}

// -------------------------------
// LOAD ALL PLANTS BUTTON
// -------------------------------
document.getElementById("loadPlantsBtn").addEventListener("click", loadPlants);

// -------------------------------
// SEARCH + SORT
// -------------------------------
function updatePlantView() {
    if (plantsLoaded) applySearchAndSort();
    else loadPlants();
}
document.getElementById("searchInput").addEventListener("input", updatePlantView);
document.getElementById("sortSelect").addEventListener("change", updatePlantView);
