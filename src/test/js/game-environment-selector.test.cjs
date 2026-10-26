"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const source = fs.readFileSync(
    path.join(__dirname, "../../main/resources/static/js/game-environment-selector.js"),
    "utf8",
);

class FakeOption {
    constructor(text, value) {
        this.text = text;
        this.value = value;
        this.disabled = false;
    }
}

class FakeSelect {
    constructor(value = "") {
        this.value = value;
        this.disabled = false;
        this.options = [];
        this.listeners = [];
    }

    replaceChildren(...options) {
        this.options = options;
        if (!options.some((option) => option.value === this.value)) {
            this.value = options[0]?.value ?? "";
        }
    }

    addEventListener(type, listener) {
        assert.equal(type, "change");
        this.listeners.push(listener);
    }

    choose(value) {
        this.value = value;
        this.listeners.forEach((listener) => listener());
    }
}

function setup(options, initial, rejectedSubmission = false) {
    const selects = Object.fromEntries(
        ["version", "type", "mortality", "participation", "leagueId"].map(
            (name) => [name, new FakeSelect(initial[name] ?? "")],
        ),
    );
    const field = { hidden: true };
    const previewClasses = new Set();
    const preview = {
        hidden: true,
        classList: {
            toggle(name, enabled) {
                if (enabled) previewClasses.add(name);
                else previewClasses.delete(name);
            },
        },
    };
    const status = { textContent: "" };
    const name = { textContent: "" };
    const nameLabel = { hidden: true };
    const description = { textContent: "" };
    const submit = { disabled: false };
    const error = { hidden: false };
    const form = {
        dataset: { rejectedSubmission: rejectedSubmission ? "true" : "" },
        elements: { namedItem: (key) => selects[key] },
        querySelector: () => submit,
    };
    const nodes = {
        'form[data-environment-selector]': form,
        "#environment-curated-options": {
            querySelectorAll: () => options.map((option) => ({ dataset: option })),
        },
        "#environment-specific-league-field": field,
        ".environment-preview": preview,
        ".environment-error": error,
        "#environment-preview-status": status,
        "#environment-preview-name": name,
        "#environment-preview-name-label": nameLabel,
        "#environment-preview-description": description,
    };
    vm.runInNewContext(source, {
        document: { querySelector: (selector) => nodes[selector] },
        Option: FakeOption,
    });
    return { selects, field, preview, previewClasses, status, name, nameLabel, description, submit, error };
}

const choice = (leagueId, leagueName, type, mortality = "SOFTCORE", participation = "SSF", version = "3.30") => ({
    leagueId, leagueName, version, type, mortality, participation,
});

test("two current fixtures filter to their curated names without changing a saved selection", () => {
    const options = [
        choice("challenge", "Development SSF league", "CHALLENGE"),
        choice("standard", "Development Standard SSF", "STANDARD"),
    ];
    const ui = setup(options, {
        version: "3.30", type: "CHALLENGE", mortality: "SOFTCORE", participation: "SSF",
    });
    assert.equal(ui.name.textContent, "Development SSF league");
    assert.equal(ui.submit.disabled, false);
    assert.equal(ui.selects.type.options[0].value, "");
    assert.equal(ui.selects.type.options[0].disabled, true);
    ui.selects.type.choose("STANDARD");
    assert.equal(ui.name.textContent, "Development Standard SSF");
    assert.equal(ui.selects.mortality.value, "SOFTCORE");
    assert.equal(ui.selects.participation.value, "SSF");
    assert.equal(ui.submit.disabled, false);
});

test("dependent choices require an explicit parent and only expose recognized combinations", () => {
    const options = [
        choice("soft", "Softcore SSF", "CHALLENGE"),
        choice("hard", "Hardcore Trade", "CHALLENGE", "HARDCORE", "TRADE"),
        choice("standard", "Standard SSF", "STANDARD"),
    ];
    const ui = setup(options, { version: "3.30", type: "" });
    assert.equal(ui.selects.type.value, "");
    assert.equal(ui.selects.type.options[0].disabled, true);
    assert.equal(ui.selects.mortality.disabled, true);
    assert.equal(ui.selects.participation.disabled, true);
    assert.equal(ui.selects.mortality.options[0].text, "Select a league type first");
    assert.equal(ui.selects.participation.options[0].text, "Select a league type first");
    assert.equal(ui.submit.disabled, true);
    assert.equal(ui.nameLabel.hidden, true);
    assert.equal(ui.previewClasses.has("environment-preview--incomplete"), true);

    ui.selects.type.choose("CHALLENGE");
    assert.equal(ui.selects.mortality.value, "");
    assert.equal(ui.selects.mortality.disabled, false);
    assert.equal(ui.selects.participation.disabled, true);
    ui.selects.mortality.choose("HARDCORE");
    assert.equal(ui.selects.participation.value, "TRADE");
    assert.equal(ui.name.textContent, "Hardcore Trade");
    assert.equal(ui.submit.disabled, false);
    assert.equal(ui.nameLabel.hidden, false);
    assert.equal(ui.previewClasses.has("environment-preview--incomplete"), false);
});

test("shared structured dimensions require the specific league to be selected", () => {
    const options = [
        choice("a", "First challenge", "CHALLENGE"),
        choice("b", "Second challenge", "CHALLENGE"),
    ];
    const ui = setup(options, {
        version: "3.30", type: "CHALLENGE", mortality: "SOFTCORE", participation: "SSF",
    });
    assert.equal(ui.field.hidden, false);
    assert.equal(ui.submit.disabled, true);
    assert.equal(ui.selects.leagueId.options[0].disabled, true);
    ui.selects.leagueId.choose("b");
    assert.equal(ui.name.textContent, "Second challenge");
    assert.equal(ui.submit.disabled, false);
});

test("an invalid server-rendered draft stays visible until the player changes it", () => {
    const ui = setup(
        [choice("standard", "Standard SSF", "STANDARD")],
        { version: "3.29", type: "STANDARD", mortality: "SOFTCORE", participation: "SSF" },
        true,
    );
    assert.equal(ui.selects.version.value, "3.29");
    assert.equal(ui.error.hidden, false);
    assert.equal(ui.preview.hidden, true);
    assert.equal(ui.submit.disabled, true);

    ui.selects.version.choose("3.30");
    assert.equal(ui.error.hidden, true);
    assert.equal(ui.preview.hidden, false);
    assert.equal(ui.name.textContent, "Standard SSF");
    assert.equal(ui.submit.disabled, false);
});
