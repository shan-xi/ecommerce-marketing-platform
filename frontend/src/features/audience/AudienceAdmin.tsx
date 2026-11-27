import {type FormEvent, useCallback, useEffect, useMemo, useState} from 'react';
import {
    createAudienceSegment,
    getAudienceSegment,
    listAudienceSegments,
    previewAudience,
    updateAudienceSegment
} from '../../shared/lib/audienceApi';
import type {
    AudienceCondition,
    AudienceField,
    AudienceOperator,
    AudiencePreview,
    AudienceRules,
    AudienceSegment
} from '../../shared/types/audience';

const fields: {
    value: AudienceField;
    label: string;
    type: 'integer' | 'decimal' | 'enum' | 'datetime';
    values?: string[]
}[] = [
    {value: 'age', label: 'Age', type: 'integer'}, {
        value: 'gender',
        label: 'Gender',
        type: 'enum',
        values: ['FEMALE', 'MALE', 'OTHER', 'UNDISCLOSED']
    },
    {value: 'registeredAt', label: 'Registered At', type: 'datetime'}, {
        value: 'membershipTier',
        label: 'Membership Tier',
        type: 'enum',
        values: ['STANDARD', 'SILVER', 'GOLD', 'PLATINUM']
    },
    {value: 'orderCount', label: 'Order Count', type: 'integer'}, {
        value: 'totalSpending',
        label: 'Total Spending (TWD)',
        type: 'decimal'
    },
    {value: 'lastOrderAt', label: 'Last Order At', type: 'datetime'}, {
        value: 'lastLoginAt',
        label: 'Last Login At',
        type: 'datetime'
    }
];
const allOps: AudienceOperator[] = ['=', '!=', '>', '>=', '<', '<=', 'IN', 'NOT IN'];

function allowedOps(field: AudienceField): AudienceOperator[] {
    return field === 'gender' || field === 'membershipTier' ? ['=', '!=', 'IN', 'NOT IN'] : allOps;
}

const blankRules = (): AudienceRules => ({
    version: 1,
    match: 'ALL',
    conditions: [{field: 'membershipTier', operator: 'IN', value: ['GOLD', 'PLATINUM']}]
});

export function AudienceAdmin() {
    const [segments, setSegments] = useState<AudienceSegment[]>([]);
    const [page, setPage] = useState(0);
    const [pageInfo, setPageInfo] = useState({number: 0, size: 20, totalElements: 0, totalPages: 0});
    const [selected, setSelected] = useState<AudienceSegment | null>(null);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [rules, setRules] = useState<AudienceRules>(blankRules());
    const [preview, setPreview] = useState<AudiencePreview | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const refresh = useCallback(async (targetPage = page) => {
        const result = await listAudienceSegments(targetPage);
        setSegments(result.data);
        setPageInfo(result.page);
    }, [page]);
    useEffect(() => {
        void refresh().catch(e => setError(message(e)));
    }, [refresh]);

    function startNew() {
        setSelected(null);
        setName('');
        setDescription('');
        setRules(blankRules());
        setPreview(null);
        setError(null);
        setNotice(null);
    }

    async function selectSegment(id: string) {
        setBusy(true);
        setError(null);
        try {
            const item = await getAudienceSegment(id);
            setSelected(item);
            setName(item.name);
            setDescription(item.description ?? '');
            setRules(item.rules);
            setPreview(null);
        } catch (e) {
            setError(message(e));
        } finally {
            setBusy(false);
        }
    }

    function updateCondition(index: number, patch: Partial<AudienceCondition>) {
        setRules(old => ({...old, conditions: old.conditions.map((c, i) => i === index ? {...c, ...patch} : c)}));
    }

    function changeField(index: number, field: AudienceField) {
        const ops = allowedOps(field);
        updateCondition(index, {
            field,
            operator: ops[0],
            value: fields.find(f => f.value === field)?.type === 'enum' ? (fields.find(f => f.value === field)?.values?.[0] ?? '') : ''
        });
    }

    function parseCondition(c: AudienceCondition): AudienceCondition {
        const spec = fields.find(f => f.value === c.field)!;
        const parse = (value: string): string | number => spec.type === 'integer' || spec.type === 'decimal' ? Number(value) : value;
        if (c.operator === 'IN' || c.operator === 'NOT IN') {
            const values = Array.isArray(c.value) ? c.value : String(c.value).split(',').map(value => value.trim());
            const parsed = values.map(value => parse(String(value)));
            return {...c, value: parsed as string[] | number[]};
        }
        return {...c, value: parse(String(c.value))};
    }

    const canonicalRules = useMemo(() => ({...rules, conditions: rules.conditions.map(parseCondition)}), [rules]);

    function validateEditor() {
        for (const condition of rules.conditions) {
            const raw = String(condition.value).trim();
            if (!raw) throw new Error('每個條件都必須填寫值。');
            if ((condition.operator === 'IN' || condition.operator === 'NOT IN') && raw.split(',').some(v => !v.trim())) throw new Error('IN / NOT IN 不可包含空值。');
            if (fields.find(f => f.value === condition.field)?.type === 'datetime' && !raw.match(/T.*(?:Z|[+-]\d{2}:?\d{2})$/)) throw new Error('日期時間必須使用含時區的 ISO-8601 格式。');
        }
    }

    async function runPreview() {
        setBusy(true);
        setError(null);
        try {
            validateEditor();
            setPreview(await previewAudience(canonicalRules));
        } catch (e) {
            setError(message(e));
        } finally {
            setBusy(false);
        }
    }

    async function save(event: FormEvent) {
        event.preventDefault();
        setBusy(true);
        setError(null);
        setNotice(null);
        try {
            validateEditor();
            const payload = {name, description: description.trim() || null, rules: canonicalRules};
            const saved = selected ? await updateAudienceSegment(selected.id, payload) : await createAudienceSegment(payload);
            setSelected(saved);
            setName(saved.name);
            setRules(saved.rules);
            if (!selected) {
                setPage(0);
                await refresh(0);
            } else {
                await refresh();
            }
            setNotice(selected ? 'Segment 已更新。' : 'Segment 已建立。');
        } catch (e) {
            setError(message(e));
        } finally {
            setBusy(false);
        }
    }

    return <section className="audience-shell">
        <div className="audience-heading">
            <div><p className="eyebrow">PHASE 4 · MEMBER AND AUDIENCE</p><h2>Audience Segments</h2><p
                className="status-detail">建立可重複使用的會員受眾條件，並預覽符合人數。</p></div>
            <button type="button" onClick={startNew}>新增 Segment</button>
        </div>
        <div className="audience-layout">
            <aside className="segment-list"><h3>Segments ({pageInfo.totalElements})</h3>{segments.length === 0 ?
                <p className="muted">目前沒有 Segment。</p> : segments.map(s => <button type="button"
                                                                                       className={`segment-item ${selected?.id === s.id ? 'selected' : ''}`}
                                                                                       key={s.id}
                                                                                       onClick={() => void selectSegment(s.id)}>
                    <strong>{s.name}</strong><small>{new Date(s.updatedAt).toLocaleString()}</small>
                </button>)}{pageInfo.totalPages > 1 && <div className="pagination">
                <button type="button" className="secondary" disabled={page === 0}
                        onClick={() => setPage(p => p - 1)}>上一頁
                </button>
                <span>{pageInfo.number + 1} / {pageInfo.totalPages}</span>
                <button type="button" className="secondary" disabled={page + 1 >= pageInfo.totalPages}
                        onClick={() => setPage(p => p + 1)}>下一頁
                </button>
            </div>}</aside>
            <form className="segment-editor" onSubmit={save}>
                <div className="editor-title"><h3>{selected ? '編輯 Segment' : '建立 Segment'}</h3>{selected &&
                    <span className="muted">{selected.id}</span>}</div>
                <label>名稱<input maxLength={120} value={name} onChange={e => setName(e.target.value)}
                                  required/></label><label>描述<textarea maxLength={500} value={description}
                                                                         onChange={e => setDescription(e.target.value)}
                                                                         rows={2}/></label>
                <div className="conditions-title"><strong>條件（ALL / AND）</strong>
                    <button type="button" className="secondary" onClick={() => setRules(r => ({
                        ...r,
                        conditions: [...r.conditions, {field: 'age', operator: '=', value: ''}]
                    }))}>新增條件
                    </button>
                </div>
                {rules.conditions.map((c, i) => {
                    const spec = fields.find(f => f.value === c.field)!;
                    return <div className="condition-row" key={i}><select aria-label="欄位" value={c.field}
                                                                          onChange={e => changeField(i, e.target.value as AudienceField)}>{fields.map(f =>
                        <option key={f.value} value={f.value}>{f.label}</option>)}</select><select aria-label="運算子"
                                                                                                   value={c.operator}
                                                                                                   onChange={e => {
                                                                                                       const op = e.target.value as AudienceOperator;
                                                                                                       const values = fields.find(f => f.value === c.field)?.values;
                                                                                                       updateCondition(i, {
                                                                                                           operator: op,
                                                                                                           value: (op === 'IN' || op === 'NOT IN') ? '' : (values?.[0] ?? '')
                                                                                                       });
                                                                                                   }}>{allowedOps(c.field).map(op =>
                        <option key={op}>{op}</option>)}</select>
                        {spec.values && c.operator !== 'IN' && c.operator !== 'NOT IN' ?
                            <select aria-label="條件值" value={String(c.value)}
                                    onChange={e => updateCondition(i, {value: e.target.value})}>{spec.values.map(v =>
                                <option key={v}>{v}</option>)}</select> : <input aria-label="條件值"
                                                                                 type={spec.type === 'datetime' || c.operator === 'IN' || c.operator === 'NOT IN' ? 'text' : 'number'}
                                                                                 step={spec.type === 'decimal' ? '0.01' : spec.type === 'integer' ? '1' : 'any'}
                                                                                 placeholder={c.operator === 'IN' || c.operator === 'NOT IN' ? '以逗號分隔多個值' : '輸入條件值'}
                                                                                 value={Array.isArray(c.value) ? c.value.join(',') : String(c.value)}
                                                                                 onChange={e => updateCondition(i, {value: e.target.value})}/>}
                        <button type="button" className="icon-button" aria-label="移除條件"
                                disabled={rules.conditions.length === 1} onClick={() => setRules(r => ({
                            ...r,
                            conditions: r.conditions.filter((_, j) => j !== i)
                        }))}>移除
                        </button>
                    </div>
                })}
                <div className="editor-actions">
                    <button type="button" className="secondary" onClick={() => void runPreview()}
                            disabled={busy}>預覽受眾
                    </button>
                    <button type="submit"
                            disabled={busy || !name.trim()}>{busy ? '處理中…' : selected ? '儲存變更' : '建立 Segment'}</button>
                </div>
                {error && <p className="error-message">{error}</p>}{notice &&
                <p className="success-message">{notice}</p>}
                {preview &&
                    <div className="preview-result"><strong>符合會員：{preview.count.toLocaleString()} 人</strong><p
                        className="muted">以下為最多 10 筆非敏感識別資料樣本</p>{preview.sample.length === 0 ?
                        <p>沒有符合條件的會員。</p> :
                        <ul>{preview.sample.map(m => <li key={m.memberId}>{m.displayName} <small>{m.memberId}</small>
                        </li>)}</ul>}</div>}
            </form>
        </div>
    </section>;
}

function message(error: unknown) {
    return error instanceof Error ? error.message : 'Request failed';
}
