import {type FormEvent, useCallback, useEffect, useState} from 'react';
import {apiRequest, AuthApiError} from '../../shared/lib/authApi';

type Coupon = {
    id: string;
    campaignId: string;
    name: string;
    description: string | null;
    discountType: 'FIXED_AMOUNT' | 'PERCENTAGE';
    discountValue: number;
    minimumOrderAmount: number | null;
    totalQuantity: number;
    claimedQuantity: number;
    startsAt: string;
    endsAt: string
};
type Write = {
    name: string;
    description: string | null;
    discountType: 'FIXED_AMOUNT' | 'PERCENTAGE';
    discountValue: number;
    minimumOrderAmount: number | null;
    totalQuantity: number;
    startsAt: string;
    endsAt: string
};
const localDate = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
const errorMessage = (e: unknown) => e instanceof AuthApiError ? `HTTP ${e.status}${e.code ? ` · ${e.code}` : ''}：${e.message}${e.traceId ? `（Trace ID: ${e.traceId}）` : ''}` : e instanceof Error ? e.message : '操作失敗';

export function CampaignCoupons({campaignId, editable}: { campaignId: string; editable: boolean }) {
    const [items, setItems] = useState<Coupon[]>([]), [editing, setEditing] = useState<Coupon | null>(null), [name, setName] = useState(''), [description, setDescription] = useState(''), [type, setType] = useState<'FIXED_AMOUNT' | 'PERCENTAGE'>('FIXED_AMOUNT'), [value, setValue] = useState('100'), [minimum, setMinimum] = useState(''), [quantity, setQuantity] = useState('100'), [starts, setStarts] = useState(localDate(new Date())), [ends, setEnds] = useState(localDate(new Date(Date.now() + 30 * 86400000))), [error, setError] = useState(''), [busy, setBusy] = useState(false);
    const refresh = useCallback(async () => {
        const r = await apiRequest<{ data: Coupon[] }>(`/api/v1/admin/campaigns/${campaignId}/coupons`);
        setItems(r.data)
    }, [campaignId]);
    useEffect(() => {
        void refresh().catch(e => setError(errorMessage(e)))
    }, [refresh]);

    function clear() {
        setEditing(null);
        setName('');
        setDescription('');
        setType('FIXED_AMOUNT');
        setValue('100');
        setMinimum('');
        setQuantity('100');
        setStarts(localDate(new Date()));
        setEnds(localDate(new Date(Date.now() + 30 * 86400000)));
    }

    function edit(c: Coupon) {
        setEditing(c);
        setName(c.name);
        setDescription(c.description ?? '');
        setType(c.discountType);
        setValue(String(c.discountValue));
        setMinimum(c.minimumOrderAmount == null ? '' : String(c.minimumOrderAmount));
        setQuantity(String(c.totalQuantity));
        setStarts(localDate(new Date(c.startsAt)));
        setEnds(localDate(new Date(c.endsAt)));
    }

    async function save(e: FormEvent) {
        e.preventDefault();
        setBusy(true);
        setError('');
        try {
            const payload: Write = {
                name,
                description: description || null,
                discountType: type,
                discountValue: Number(value),
                minimumOrderAmount: minimum === '' ? null : Number(minimum),
                totalQuantity: Number(quantity),
                startsAt: new Date(starts).toISOString(),
                endsAt: new Date(ends).toISOString()
            };
            await apiRequest(`/api/v1/admin/campaigns/${campaignId}/coupons${editing ? `/${editing.id}` : ''}`, {
                method: editing ? 'PUT' : 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(payload)
            });
            clear();
            await refresh()
        } catch (e) {
            setError(errorMessage(e))
        } finally {
            setBusy(false)
        }
    }

    return <section className="audience-shell" style={{marginTop: 20}}>
        <div className="audience-heading">
            <div><p className="eyebrow">PHASE 6 · COUPON CONFIGURATION</p><h2>Campaign Coupons</h2><p
                className="status-detail">管理此 Campaign 的優惠券、折扣、數量與有效期間。</p></div>
            {editable && <button type="button" onClick={clear}>新增 Coupon</button>}</div>
        {error && <p className="error-message" role="alert">{error}</p>}
        <div className="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>名稱</th>
                    <th>折扣</th>
                    <th>數量</th>
                    <th>有效期間</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>{items.map(c => <tr key={c.id}>
                    <td><strong>{c.name}</strong><small>{c.description}</small></td>
                    <td>{c.discountType === 'PERCENTAGE' ? `${c.discountValue}%` : `NT$ ${c.discountValue}`}</td>
                    <td>{c.claimedQuantity} / {c.totalQuantity}</td>
                    <td>{new Date(c.startsAt).toLocaleString()} – {new Date(c.endsAt).toLocaleString()}</td>
                    <td>{editable &&
                        <button className="secondary" type="button" onClick={() => edit(c)}>編輯</button>}</td>
                </tr>)}{items.length === 0 && <tr>
                    <td colSpan={5}>尚未設定 Coupon。</td>
                </tr>}</tbody>
            </table>
        </div>
        {editable && <form className="segment-editor" style={{marginTop: 18}} onSubmit={save}>
            <div className="editor-title"><h3>{editing ? '編輯 Coupon' : '新增 Coupon'}</h3>{editing &&
                <button type="button" className="secondary" onClick={clear}>取消編輯</button>}</div>
            <label>名稱<input required maxLength={160} value={name}
                              onChange={e => setName(e.target.value)}/></label><label>描述<textarea rows={2}
                                                                                                    value={description}
                                                                                                    onChange={e => setDescription(e.target.value)}/></label>
            <div className="campaign-dates"><label>折扣類型<select value={type}
                                                                   onChange={e => setType(e.target.value as typeof type)}>
                <option value="FIXED_AMOUNT">固定金額 (TWD)</option>
                <option value="PERCENTAGE">百分比 (%)</option>
            </select></label><label>折扣值<input required type="number" min="0.01"
                                                 max={type === 'PERCENTAGE' ? 100 : undefined} step="0.01" value={value}
                                                 onChange={e => setValue(e.target.value)}/></label></div>
            <div className="campaign-dates"><label>最低訂單金額 (選填)<input type="number" min="0" step="0.01"
                                                                             value={minimum}
                                                                             onChange={e => setMinimum(e.target.value)}/></label><label>總數量<input
                required type="number" min={editing ? editing.claimedQuantity : 1} step="1" value={quantity}
                onChange={e => setQuantity(e.target.value)}/></label></div>
            <div className="campaign-dates"><label>開始時間<input required type="datetime-local" value={starts}
                                                                  onChange={e => setStarts(e.target.value)}/></label><label>結束時間<input
                required type="datetime-local" value={ends} onChange={e => setEnds(e.target.value)}/></label></div>
            <div className="editor-actions">
                <button type="submit" disabled={busy}>{busy ? '儲存中…' : '儲存 Coupon'}</button>
            </div>
        </form>}
        {!editable && <p className="muted">Campaign 非 DRAFT 狀態，Coupon 僅供檢視。</p>}</section>
}
