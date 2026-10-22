(() => {
    const EXAMPLES = {
        484: ['Ecatepec de Moaalos', 'starbucs coefe', 'farmacias del ahoro', 'bodega aurerra ', 'Iglesia de San Juan Bautsta'],
        826: ['The Toeer of London', 'Starbucls coff', 'aple', 'buckingam palace ', 'sainsburys locla'],
        458: ['petronas twin towrs ', 'petronas twin towrs', 'masjid jamk', 'mid valey megamall ', 'klinik kesihtan'],
        360: ['masjid istiqal ', 'masjid istiqal', 'indomaret ', 'apotek kimia frama', 'stasiun gamir'],
    };
    const NAMES = {CHANGE: '改写', CHANNEL: '通道', EDITS: '编辑', EDIT_RATIO: '编辑比', LOG_FREQ: 'log频次', OOV: '未登录',
        CITY_FREQ: '城市频', CITY_SPREAD: '城市覆盖', EXPOSE: '曝光', ERRDICT_LOGP: '纠错词典logP', ERRDICT_HIT: '纠错词典',
        TONE_MATCH: '音码同', FIRST_CHAR: '首字改', LEN_DIFF: '长度差', SPLIT_MERGE: '拆并', PREFIX_CHANGE: '前缀改写',
        CTX_PMI: '上下文PMI', CTX_SEEN: '共现', CTX_UNKNOWN: '上下文未知'};
    const STATUS = {CANDIDATES: '参与纠错', TRUSTED: '判定正确(跳过)', NOT_CORRECTABLE: '不纠错(短词/数字等)'};

    const $ = (id) => document.getElementById(id);
    const state = {country: 484, city: 0, meta: null, seq: 0, timer: null};

    function esc(s) {
        return String(s).replace(/[&<>"]/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]));
    }

    async function loadMeta() {
        try {
            const r = await fetch('/api/v1/meta');
            if (!r.ok) throw new Error('HTTP ' + r.status);
            state.meta = await r.json();
            const m = state.meta;
            $('meta').innerHTML = [
                `数据源 ${esc(m.source)}`,
                `模型 ${esc(m.model.version)}`,
                `MEDIUM≥${m.model.mediumThreshold.toFixed(2)} · HIGH≥${m.model.highThreshold.toFixed(2)}`,
                `纠错词典 ${m.errorDictionaryEntries} 条`,
            ].map(t => `<span class="pill">${t}</span>`).join('');
            renderCountries();
        } catch (e) {
            $('meta').textContent = '模型信息加载失败：' + e.message;
        }
    }

    function renderCountries() {
        const box = $('countries');
        box.innerHTML = '';
        const list = state.meta.countries;
        if (!list.find(c => c.countryId === state.country) && list.length) state.country = list[0].countryId;
        for (const c of list) {
            const b = document.createElement('button');
            b.textContent = `${c.code} · ${c.name}`;
            b.title = `${c.tokens} tokens · ${c.bigramPairs} bigrams`;
            b.className = c.countryId === state.country ? 'active' : '';
            b.onclick = () => { state.country = c.countryId; state.city = 0; renderCountries(); schedule(0); };
            box.appendChild(b);
        }
        const cur = list.find(c => c.countryId === state.country);
        const sel = $('city');
        sel.innerHTML = '<option value="0">未知城市</option>' + (cur ? cur.cities : [])
            .map(ci => `<option value="${ci.id}">${esc(ci.name)}</option>`).join('');
        if (cur && cur.cities.length && !state.city) state.city = cur.cities[0].id;
        sel.value = String(state.city);
        renderExamples();
    }

    function renderExamples() {
        const box = $('examples');
        box.innerHTML = '';
        const all = [];
        for (const [cid, qs] of Object.entries(EXAMPLES)) {
            for (const q of qs) all.push([+cid, q]);
        }
        all.sort((a, b) => (a[0] === state.country ? -1 : 0) - (b[0] === state.country ? -1 : 0));
        for (const [cid, q] of all) {
            const country = state.meta.countries.find(c => c.countryId === cid);
            if (!country) continue;
            const b = document.createElement('button');
            b.className = 'chip';
            b.innerHTML = `<span class="cc">${esc(country.code)}</span>${esc(q.endsWith(' ') ? q.trimEnd() + ' ␣' : q)}`;
            b.onclick = () => {
                if (state.country !== cid) {
                    state.country = cid;
                    state.city = 0;
                    renderCountries();
                }
                $('q').value = q;
                $('q').focus();
                schedule(0);
            };
            box.appendChild(b);
        }
    }

    function schedule(delay) {
        clearTimeout(state.timer);
        state.timer = setTimeout(run, delay ?? 120);
    }

    async function run() {
        const q = $('q').value;
        if (!q.trim()) {
            $('empty').hidden = false;
            $('result-body').hidden = true;
            $('lattice-card').hidden = true;
            $('error').hidden = true;
            return;
        }
        const seq = ++state.seq;
        $('result').classList.add('loading');
        try {
            const url = `/api/v1/correct?q=${encodeURIComponent(q)}&country=${state.country}&city=${state.city}&debug=true`;
            const r = await fetch(url);
            if (!r.ok) throw new Error('HTTP ' + r.status);
            const res = await r.json();
            if (seq !== state.seq) return;
            render(res);
            $('error').hidden = true;
        } catch (e) {
            if (seq !== state.seq) return;
            $('error').hidden = false;
            $('error').textContent = '纠错服务请求失败：' + e.message + '（Sug 侧应 fail-open，直接使用原始输入）';
        } finally {
            if (seq === state.seq) $('result').classList.remove('loading');
        }
    }

    function highlight(text, parts, cls) {
        let html = '';
        let cursor = 0;
        for (const p of parts) {
            const at = text.indexOf(p, cursor);
            if (at < 0) continue;
            html += esc(text.slice(cursor, at)) + `<mark class="${cls}">${esc(p)}</mark>`;
            cursor = at + p.length;
        }
        return html + esc(text.slice(cursor));
    }

    function render(res) {
        $('empty').hidden = true;
        $('result-body').hidden = false;
        const applied = (res.corrections || []).filter(c => c.applied);
        $('original').innerHTML = highlight(res.original, applied.map(c => c.from), 'err');
        $('corrected').innerHTML = highlight(res.corrected, applied.map(c => c.to), 'fix');

        const prefix = (res.debug || []).some(t => t.prefixMode);
        const badges = [
            `<span class="badge ${res.level}">${res.changed ? '已纠错 · ' + res.level : res.skipReason ? '未处理 · ' + res.skipReason : '无需纠错'}</span>`,
            res.changed ? `<span class="badge">置信度 ${res.confidence.toFixed(3)}</span>` : '',
            `<span class="badge">${prefix ? '末词前缀模式（不补全）' : '末词已输入完整'}</span>`,
            `<span class="badge">耗时 ${(res.latencyMicros / 1000).toFixed(2)} ms</span>`,
        ];
        $('badges').innerHTML = badges.join('');

        $('corrections').innerHTML = (res.corrections || []).map(c => `
            <li class="${c.applied ? '' : 'skipped'}">
                <span class="from">${esc(c.from)}</span> → <span class="to">${esc(c.to)}</span>
                <span class="badge ${c.level}">${c.level} ${c.confidence.toFixed(3)}</span>
                <span class="badge">编辑 ${c.edits}</span>
                ${c.applied ? '' : '<span class="badge">置信度不足，未应用</span>'}
                <span class="src">${c.sources.map(esc).join(' · ')}</span>
            </li>`).join('');

        renderLattice(res.debug || []);
    }

    function renderLattice(tokens) {
        $('lattice-card').hidden = tokens.length === 0;
        $('lattice').innerHTML = tokens.map(t => {
            const tags = [
                `<span class="tag ${t.status === 'TRUSTED' ? 'trusted' : t.status === 'NOT_CORRECTABLE' ? 'skip' : ''}">${STATUS[t.status] || t.status}</span>`,
                t.prefixMode ? '<span class="tag prefix">正在输入 · 前缀匹配</span>' : '',
            ].join('');
            const rows = t.candidates.map(c => `
                <tr class="${c.chosen ? 'chosen' : ''}">
                    <td class="out">${esc(c.output)}${c.keep ? ' <span class="src-tag">原词</span>' : ''}</td>
                    <td>${c.sources.map(s => `<span class="src-tag">${esc(s)}</span>`).join('')}</td>
                    <td class="num">${c.edits}</td>
                    <td class="num">${c.channelCost.toFixed(2)}</td>
                    <td class="num">${c.nodeScore.toFixed(2)}</td>
                    <td class="num">${c.maxMarginal.toFixed(2)}</td>
                    <td class="feat">${Object.entries(c.features).map(([k, v]) => `${NAMES[k] || k}=${v}`).join(' · ')}</td>
                </tr>`).join('');
            return `
                <div class="token">
                    <div class="token-head"><span class="raw">#${t.index} ${esc(t.raw)}</span>
                        <span class="src-tag">key: ${esc(t.norm)}</span>${tags}</div>
                    <div class="table-wrap"><table>
                        <thead><tr><th>候选</th><th>召回来源</th><th>编辑</th><th>通道代价</th><th>节点分</th><th>max-marginal</th><th>特征</th></tr></thead>
                        <tbody>${rows}</tbody>
                    </table></div>
                </div>`;
        }).join('');
    }

    $('q').addEventListener('input', () => schedule());
    $('city').addEventListener('change', e => { state.city = +e.target.value; schedule(0); });
    $('space').addEventListener('click', () => {
        const el = $('q');
        if (!el.value.endsWith(' ')) el.value += ' ';
        el.focus();
        schedule(0);
    });
    $('clear').addEventListener('click', () => { $('q').value = ''; $('q').focus(); schedule(0); });

    loadMeta().then(() => {
        $('q').value = 'Ecatepec de Moaalos';
        schedule(0);
    });
})();
