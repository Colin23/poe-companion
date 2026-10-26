"use strict";

// Improve the curated environment form without replacing server-side validation or form submission.
const environmentForm = document.querySelector("form[data-environment-selector]");
const environmentRegistry = document.querySelector("#environment-curated-options");

if (environmentForm && environmentRegistry) {
    const options = Array.from(environmentRegistry.querySelectorAll("[data-environment-option]"), (entry) => ({
        version: entry.dataset.version,
        type: entry.dataset.type,
        mortality: entry.dataset.mortality,
        participation: entry.dataset.participation,
        leagueId: entry.dataset.leagueId,
        name: entry.dataset.leagueName,
    }));

    const dimensions = [
        { name: "version", label: "a version" },
        { name: "type", label: "a league type" },
        { name: "mortality", label: "mortality" },
        { name: "participation", label: "participation" },
    ];
    const selectors = Object.fromEntries(
        dimensions.map(({ name }) => [name, environmentForm.elements.namedItem(name)]),
    );
    const specificLeagueField = document.querySelector("#environment-specific-league-field");
    const specificLeague = environmentForm.elements.namedItem("leagueId");
    const preview = document.querySelector(".environment-preview");
    const previewStatus = document.querySelector("#environment-preview-status");
    const previewName = document.querySelector("#environment-preview-name");
    const previewDescription = document.querySelector("#environment-preview-description");
    const submit = environmentForm.querySelector('button[type="submit"]');

    const friendly = {
        CHALLENGE: "Challenge",
        STANDARD: "Standard",
        SOFTCORE: "Softcore",
        HARDCORE: "Hardcore",
        TRADE: "Trade",
        SSF: "SSF",
    };

    function replaceChoices(select, values, previous, placeholder) {
        const chosen = values.includes(previous) ? previous : values.length === 1 ? values[0] : "";
        const available = values.length > 0;
        const optionsForSelect = values.map((value) => new Option(friendly[value] || value, value));
        if (values.length !== 1) {
            optionsForSelect.unshift(new Option(placeholder, ""));
        }
        select.replaceChildren(...optionsForSelect);
        select.value = chosen;
        select.disabled = !available;
    }

    function showPreview(status, name, description, canSave) {
        previewStatus.textContent = status;
        previewName.textContent = name;
        previewDescription.textContent = description;
        submit.disabled = !canSave;
    }

    function refreshOptions() {
        // Each dimension filters the next. Keep downstream values only while they are still valid.
        for (const [index, dimension] of dimensions.entries()) {
            const candidates = options.filter((entry) =>
                dimensions.slice(0, index).every(({ name }) =>
                    selectors[name].value !== "" && entry[name] === selectors[name].value),
            );
            const values = [...new Set(candidates.map((entry) => entry[dimension.name]))];
            replaceChoices(
                selectors[dimension.name],
                values,
                selectors[dimension.name].value,
                "Choose " + dimension.label,
            );
        }

        const matches = options.filter((entry) =>
            dimensions.every(({ name }) => selectors[name].value !== "" && entry[name] === selectors[name].value),
        );

        specificLeagueField.hidden = matches.length <= 1;
        specificLeague.disabled = matches.length <= 1;
        if (matches.length > 1) {
            const previous = specificLeague.value;
            specificLeague.replaceChildren(
                new Option("Choose a specific league", ""),
                ...matches.map((entry) => new Option(entry.name, entry.leagueId)),
            );
            specificLeague.value = matches.some((entry) => entry.leagueId === previous) ? previous : "";
        } else {
            specificLeague.value = "";
        }

        const selected = matches.length === 1
            ? matches[0]
            : matches.find((entry) => entry.leagueId === specificLeague.value && specificLeague.value !== "");
        if (!selected) {
            const ambiguous = matches.length > 1;
            showPreview(
                ambiguous ? "Specific league required" : "Choose a recognized environment",
                ambiguous ? "Choose a league above" : "Complete the selection above",
                ambiguous ? "Multiple curated leagues share these settings." : "Only curated combinations can be saved.",
                false,
            );
            return;
        }

        const description = dimensions.map(({ name }) => friendly[selected[name]] || selected[name]).join(" · ");
        showPreview("Recognized environment", selected.name, description, true);
    }

    for (const dimension of dimensions) {
        selectors[dimension.name].addEventListener("change", refreshOptions);
    }
    specificLeague.addEventListener("change", refreshOptions);
    refreshOptions();
    preview.hidden = false;
}
