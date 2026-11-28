import {type FormEvent, useCallback, useEffect, useState} from 'react';
import {listAudienceSegments} from '../../shared/lib/audienceApi';
import {
    createCampaign,
    getCampaign,
    listCampaigns,
    previewCampaignAudience,
    updateCampaign
} from '../../shared/lib/campaignApi';
import type {AudiencePreview, AudienceRules, AudienceSegment} from '../../shared/types/audience';
import type {Campaign, CampaignStatus, CampaignWrite} from '../../shared/types/campaign';
import {CampaignCoupons} from './CampaignCoupons';
import {CampaignLifecycle} from './CampaignLifecycle';

const emptyRules = (): AudienceRules => ({
    version: 1,
    match: 'ALL',
    conditions: [{field: 'age', operator: '>=', value: 18}]
});
const dateValue = (value?: string) => value ? new Date(value).toISOString().slice(0, 16) : '';
const errorText = (e: unknown) => e instanceof Error ? e.message : '發生未知錯誤。';

export function CampaignAdmin() {
    const [rows, setRows] = useState<Campaign[]>([]);
    const [segments, setSegments] = useState<AudienceSegment[]>([]);
    const [page, setPage] = useState(0);
    const [pageInfo, setPageInfo] = useState({number: 0, size: 20, totalElements: 0, totalPages: 0});
    const [status, setStatus] = useState<CampaignStatus | ''>('');
    const [selected, setSelected] = useState<Campaign | null>(null);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [startsAt, setStartsAt] = useState('');
    const [endsAt, setEndsAt] = useState('');
    const [headline, setHeadline] = useState('');
    const [body, setBody] = useState('');
    const [segmentId, setSegmentId] = useState('');
    const [useRules, setUseRules] = useState(false);
    const [rulesText, setRulesText] = useState(JSON.stringify(emptyRules(), null, 2));
    const [preview, setPreview] = useState<AudiencePreview | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const readOnly = Boolean(selected && selected.status !== 'DRAFT');
    const refresh = useCallback(async (p = page, filter = status) => {
        const result = await listCampaigns(p, 20, filter);
        setRows(result.data as Campaign[]);
        setPageInfo(result.page);
    }, [page, status]);
    useEffect(() => {
        void refresh().catch(e => setError(errorText(e)));
    }, [refresh]);
    useEffect(() => {
        listAudienceSegments(0, 100).then(r => setSegments(r.data)).catch(e => setError(errorText(e)));
    }, []);

    function reset() {
        setSelected(null);
        setName('');
        setDescription('');
        setStartsAt('');
        setEndsAt('');
        setHeadline('');
        setBody('');
        setSegmentId('');
        setUseRules(false);
        setRulesText(JSON.stringify(emptyRules(), null, 2));
        setPreview(null);
        setError(null);
        setNotice(null);
    }

    async function openCampaign(id: string) {
        setBusy(true);
        setError(null);
        try {
            const c = await getCampaign(id);
            setSelected(c);
            setName(c.name);
            setDescription(c.description ?? '');
            setStartsAt(dateValue(c.startsAt));
            setEndsAt(dateValue(c.endsAt));
            setHeadline(c.landingPage.headline);
            setBody(c.landingPage.body);
            setSegmentId(c.audience.segmentId ?? '');
            setUseRules(Boolean(c.audience.rules));
            setRulesText(JSON.stringify(c.audience.rules ?? emptyRules(), null, 2));
            setPreview(null);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    function payload(): CampaignWrite {
        let rules: AudienceRules | null = null;
        if (useRules) {
            try {
                rules = JSON.parse(rulesText) as AudienceRules;
            } catch {
                throw new Error('Audience rules 必須是有效 JSON。');
            }
        }
        if (!segmentId && !rules) throw new Error('請選擇 Segment 或啟用 campaign rules。');
        if (!startsAt || !endsAt) throw new Error('請填寫開始與結束時間。');
        return {
            name,
            description: description.trim() || null,
            startsAt: new Date(startsAt).toISOString(),
            endsAt: new Date(endsAt).toISOString(),
            landingPage: {headline, body},
            audience: {segmentId: segmentId || null, ...(useRules ? {rules: rules!} : {})}
        };
    }

    async function save(e: FormEvent) {
        e.preventDefault();
        setBusy(true);
        setError(null);
        setNotice(null);
        try {
            const input = payload();
            const c = selected ? await updateCampaign(selected.id, input) : await createCampaign(input);
            setSelected(c);
            setNotice(selected ? 'Campaign draft 已更新。' : 'Campaign draft 已建立。');
            await refresh(page, status);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    async function runPreview() {
        if (!selected) {
            setError('請先儲存 campaign，再預覽已儲存的 audience。');
            return;
        }
        setBusy(true);
        setError(null);
        try {
            setPreview(await previewCampaignAudience(selected.id));
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    return <section className="audience-shell campaign-shell">
        <div className="audience-heading">
            <div><p className="eyebrow">PHASE 6 · CAMPAIGN & COUPONS</p><h2>Campaign Management</h2><p
                className="status-detail">管理 Campaign Draft、預覽受眾，並在已選取的草稿中設定 Coupons。</p></div>
            <button type="button" onClick={reset}>新增 Campaign</button>
        </div>
        {error && <p className="error-message" role="alert">{error}</p>}{notice &&
        <p className="success-message">{notice}</p>}
        <div className="campaign-toolbar"><label>狀態篩選<select value={status} onChange={e => {
            const v = e.target.value as CampaignStatus | '';
            setStatus(v);
            setPage(0);
            void refresh(0, v).catch(err => setError(errorText(err)));
        }}>
            <option value="">全部狀態</option>
            <option value="DRAFT">DRAFT</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="PAUSED">PAUSED</option>
            <option value="ENDED">ENDED</option>
        </select></label><span className="muted">共 {pageInfo.totalElements} 筆</span></div>
        <div className="campaign-layout">
            <aside className="segment-list"><h3>Campaigns</h3>{rows.length === 0 ?
                <p className="muted">目前沒有 Campaign。</p> : rows.map(c => <button type="button" key={c.id}
                                                                                    className={`segment-item ${selected?.id === c.id ? 'selected' : ''}`}
                                                                                    onClick={() => void openCampaign(c.id)}>
                    <strong>{c.name}</strong><small>{c.status} · {new Date(c.createdAt).toLocaleDateString()}</small>
                </button>)}{pageInfo.totalPages > 1 && <div className="pagination">
                <button className="secondary" disabled={page === 0} onClick={() => {
                    setPage(p => p - 1);
                    void refresh(page - 1);
                }}>上一頁
                </button>
                <span>{page + 1}/{pageInfo.totalPages}</span>
                <button className="secondary" disabled={page + 1 >= pageInfo.totalPages} onClick={() => {
                    setPage(p => p + 1);
                    void refresh(page + 1);
                }}>下一頁
                </button>
            </div>}</aside>
            <form className="segment-editor" onSubmit={save}>
                <div className="editor-title">
                    <h3>{selected ? 'Campaign 詳情 / 編輯' : '建立 Campaign Draft'}</h3>{selected &&
                    <span className="muted">{selected.id} · {selected.status}</span>}</div>
                <label>名稱<input maxLength={160} required disabled={readOnly} value={name}
                                  onChange={e => setName(e.target.value)}/></label><label>描述<textarea
                disabled={readOnly} rows={2} value={description}
                onChange={e => setDescription(e.target.value)}/></label>
                <div className="campaign-dates"><label>開始時間<input type="datetime-local" required disabled={readOnly}
                                                                      value={startsAt}
                                                                      onChange={e => setStartsAt(e.target.value)}/></label><label>結束時間<input
                    type="datetime-local" required disabled={readOnly} value={endsAt}
                    onChange={e => setEndsAt(e.target.value)}/></label></div>
                <label>Landing page headline<input maxLength={200} required disabled={readOnly} value={headline}
                                                   onChange={e => setHeadline(e.target.value)}/></label><label>Landing
                page body<textarea maxLength={10000} rows={4} required disabled={readOnly} value={body}
                                   onChange={e => setBody(e.target.value)}/></label>
                <label>Saved audience segment<select disabled={readOnly} value={segmentId}
                                                     onChange={e => setSegmentId(e.target.value)}>
                    <option value="">不使用 Segment</option>
                    {segments.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
                <label className="inline-check"><input type="checkbox" disabled={readOnly} checked={useRules}
                                                       onChange={e => setUseRules(e.target.checked)}/> 同時加入
                    campaign-specific rules（與 Segment 使用 AND）</label>{useRules &&
                <label>Audience rules JSON<textarea disabled={readOnly} rows={10} value={rulesText}
                                                    onChange={e => setRulesText(e.target.value)}/></label>}
                <div className="editor-actions">
                    <button type="submit" disabled={busy || readOnly}>{busy ? '處理中…' : '儲存 Draft'}</button>
                    <button type="button" className="secondary" disabled={busy || !selected}
                            onClick={() => void runPreview()}>預覽已儲存受眾
                    </button>
                </div>
                {preview && <div className="preview-result"><strong>預估符合人數：{preview.count}</strong><p
                    className="muted">此結果為預覽當下估算，非發布時的最終受眾快照。</p>
                    <ul>{preview.sample.map(m => <li key={m.memberId}>{m.displayName} <small>{m.memberId}</small>
                    </li>)}</ul>
                </div>}</form>
        </div>
        {selected && selected.status !== 'DRAFT' &&
            <p className="analytics-entry"><a href={`/admin/campaigns/${selected.id}/analytics`}>查看 Campaign Analytics
                →</a></p>}
        {selected && <><CampaignCoupons campaignId={selected.id}
                                        editable={selected.status === 'DRAFT'}/><CampaignLifecycle
            campaignId={selected.id} campaignStatus={selected.status} onUpdated={async () => {
            await openCampaign(selected.id);
            await refresh(page, status);
        }}/></>}
    </section>;
}
