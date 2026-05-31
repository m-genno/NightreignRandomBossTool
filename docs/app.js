"use strict";

// ====== データモデル（Android版と同じ仕様） ======
const WILDCARD = "*";

const state = {
    readings: new Map(), // 夜ボス名 -> よみがな
    nightlords: [],      // { name, night1:Set, night2:Set }
    day1: null,          // 選択された1日目の夜ボス名
    day2: null,          // 選択された2日目の夜ボス名
};

// ====== 検索用の正規化（カタカナ→ひらがな・小書き→大書き・小文字化） ======
// 「ぃ」「ィ」を「い」と同一視するため、小書き文字を通常文字に畳む。
const SMALL_KANA_FOLD = {
    "ぁ": "あ", "ぃ": "い", "ぅ": "う", "ぇ": "え", "ぉ": "お",
    "っ": "つ", "ゃ": "や", "ゅ": "ゆ", "ょ": "よ", "ゎ": "わ",
    "ゕ": "か", "ゖ": "け",
};

function normalize(s) {
    let out = "";
    for (const ch of s) {
        let c = ch;
        const code = ch.codePointAt(0);
        if (code >= 0x30a1 && code <= 0x30f6) {
            c = String.fromCodePoint(code - 0x60); // カタカナ→ひらがな
        }
        if (SMALL_KANA_FOLD[c]) c = SMALL_KANA_FOLD[c]; // 小書き→通常
        out += c;
    }
    return out.toLowerCase();
}

function bossMatches(boss, query) {
    const q = normalize(query.trim());
    if (q === "") return true;
    return normalize(boss.name).includes(q) || normalize(boss.reading).includes(q);
}

// ====== パース ======
function cleanLines(text) {
    return text
        .split(/\r?\n/)
        .map((l) => l.trim())
        .filter((l) => l !== "" && !l.startsWith("#"));
}

function parseReadings(text) {
    const map = new Map();
    for (const line of cleanLines(text)) {
        const parts = line.split(",").map((p) => p.trim());
        const name = parts[0];
        if (!name) continue;
        map.set(name, parts[1] && parts[1] !== "" ? parts[1] : name);
    }
    return map;
}

function parseBossSet(s) {
    const t = (s || "").trim();
    if (t === "") return new Set();
    if (t === "*" || t === "全" || t === "全種類") return new Set([WILDCARD]);
    return new Set(
        t.split(/[;；、/]/).map((x) => x.trim()).filter((x) => x !== "")
    );
}

function parseNightlords(text) {
    const list = [];
    for (const line of cleanLines(text)) {
        const parts = line.split("|").map((p) => p.trim());
        const name = parts[0];
        if (!name) continue;
        list.push({
            name,
            night1: parseBossSet(parts[1]),
            night2: parseBossSet(parts[2]),
        });
    }
    return list;
}

// ====== 候補ロジック ======
function bossOf(name) {
    return { name, reading: state.readings.get(name) || name };
}

function choicesFrom(selector) {
    const names = new Set();
    for (const lord of state.nightlords) {
        for (const b of selector(lord)) if (b !== WILDCARD) names.add(b);
    }
    return [...names]
        .map(bossOf)
        .sort((a, b) => a.reading.localeCompare(b.reading, "ja"));
}

function matchesNight(set, boss) {
    return set.has(WILDCARD) || set.has(boss);
}

function candidates() {
    const { day1, day2 } = state;
    if (!day1 && !day2) return [];
    return state.nightlords
        .filter(
            (lord) =>
                (!day1 || matchesNight(lord.night1, day1)) &&
                (!day2 || matchesNight(lord.night2, day2))
        )
        .map((lord) => lord.name);
}

// ====== UI: コンボボックス ======
function setupCombo(comboEl, getChoices, onSelect, onClearSelection) {
    const input = comboEl.querySelector("input");
    const list = comboEl.querySelector(".suggestions");
    const clearBtn = comboEl.querySelector(".clear");
    let activeIndex = -1;

    function currentFiltered() {
        const choices = getChoices();
        const q = input.value;
        return q.trim() === "" ? choices : choices.filter((b) => bossMatches(b, q));
    }

    function render() {
        const filtered = currentFiltered().slice(0, 60);
        list.innerHTML = "";
        activeIndex = -1;
        if (filtered.length === 0) {
            list.hidden = true;
            return;
        }
        filtered.forEach((boss, i) => {
            const li = document.createElement("li");
            li.dataset.name = boss.name;
            li.dataset.index = String(i);
            const showReading = boss.reading && boss.reading !== boss.name;
            li.innerHTML =
                escapeHtml(boss.name) +
                (showReading ? `<span class="reading">${escapeHtml(boss.reading)}</span>` : "");
            list.appendChild(li);
        });
        list.hidden = false;
    }

    function open() { render(); }
    function close() { list.hidden = true; activeIndex = -1; }

    function pick(name) {
        input.value = name;
        clearBtn.hidden = false;
        onSelect(name);
        close();
    }

    input.addEventListener("focus", open);
    input.addEventListener("input", () => {
        clearBtn.hidden = input.value === "";
        onClearSelection(); // 編集中は選択を解除
        open();
    });

    input.addEventListener("keydown", (e) => {
        const items = [...list.querySelectorAll("li")];
        if (e.key === "ArrowDown" && items.length) {
            e.preventDefault();
            activeIndex = Math.min(activeIndex + 1, items.length - 1);
        } else if (e.key === "ArrowUp" && items.length) {
            e.preventDefault();
            activeIndex = Math.max(activeIndex - 1, 0);
        } else if (e.key === "Enter") {
            if (activeIndex >= 0 && items[activeIndex]) {
                e.preventDefault();
                pick(items[activeIndex].dataset.name);
            }
            return;
        } else if (e.key === "Escape") {
            close();
            return;
        } else {
            return;
        }
        items.forEach((li, i) => li.classList.toggle("active", i === activeIndex));
        if (items[activeIndex]) items[activeIndex].scrollIntoView({ block: "nearest" });
    });

    list.addEventListener("mousedown", (e) => {
        // blur より先に拾うため mousedown
        const li = e.target.closest("li");
        if (li) { e.preventDefault(); pick(li.dataset.name); }
    });

    clearBtn.addEventListener("click", () => {
        input.value = "";
        clearBtn.hidden = true;
        onClearSelection();
        close();
        input.focus();
    });

    document.addEventListener("click", (e) => {
        if (!comboEl.contains(e.target)) close();
    });

    // 外部リセット用
    comboEl._reset = () => {
        input.value = "";
        clearBtn.hidden = true;
        close();
    };
    comboEl._refresh = () => { if (!list.hidden) render(); };
}

function escapeHtml(s) {
    return s.replace(/[&<>"']/g, (c) =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])
    );
}

// ====== 結果表示 ======
function renderResults() {
    const el = document.getElementById("results");
    const { day1, day2 } = state;
    let html = "<h2>3日目のボス候補</h2>";

    if (!day1 && !day2) {
        html += `<p class="hint">1日目か2日目の夜ボスを選択してください。</p>`;
    } else {
        const list = candidates();
        if (list.length === 0) {
            html += `<p class="error">該当する候補が見つかりませんでした。データ（nightlords.txt）を確認してください。</p>`;
        } else {
            html += `<p class="count">${list.length} 件</p>`;
            html += list.map((n) => `<div class="candidate">${escapeHtml(n)}</div>`).join("");
        }
    }
    el.innerHTML = html;
}

// ====== 初期化 ======
const combo1 = document.querySelector('.combo[data-day="1"]');
const combo2 = document.querySelector('.combo[data-day="2"]');

async function loadData() {
    const bust = "?t=" + Date.now();
    const [bossesText, lordsText] = await Promise.all([
        fetch("data/bosses.txt" + bust).then((r) => r.text()),
        fetch("data/nightlords.txt" + bust).then((r) => r.text()),
    ]);
    state.readings = parseReadings(bossesText);
    state.nightlords = parseNightlords(lordsText);
    combo1._refresh && combo1._refresh();
    combo2._refresh && combo2._refresh();
    renderResults();
}

function resetAll() {
    state.day1 = null;
    state.day2 = null;
    combo1._reset();
    combo2._reset();
    renderResults();
}

setupCombo(
    combo1,
    () => choicesFrom((l) => l.night1),
    (name) => { state.day1 = name; renderResults(); },
    () => { if (state.day1 !== null) { state.day1 = null; renderResults(); } }
);
setupCombo(
    combo2,
    () => choicesFrom((l) => l.night2),
    (name) => { state.day2 = name; renderResults(); },
    () => { if (state.day2 !== null) { state.day2 = null; renderResults(); } }
);

document.getElementById("resetBtn").addEventListener("click", resetAll);
document.getElementById("reloadBtn").addEventListener("click", () => loadData());

loadData().catch((err) => {
    document.getElementById("results").innerHTML =
        `<p class="error">データの読み込みに失敗しました: ${escapeHtml(String(err))}</p>`;
});
