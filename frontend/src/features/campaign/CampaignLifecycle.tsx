import {useCallback, useEffect, useState} from 'react';
import {
    type CampaignMessage,
    type DeliveryStatus,
    getCampaignMessage,
    getDeliveryStatus,
    pauseCampaign,
    publishCampaign,
    retryDelivery,
    saveCampaignMessage
} from '../../shared/lib/campaignApi';
import {AuthApiError} from '../../shared/lib/authApi';

type Props = { campaignId: string; campaignStatus: string; onUpdated: () => void };
const errorText = (e: unknown) => e instanceof Error ? e.message : '發生未知錯誤。';

export function CampaignLifecycle({campaignId, campaignStatus, onUpdated}: Props) {
    const [message, setMessage] = useState<CampaignMessage | null>(null);
    const [title, setTitle] = useState('');
    const [body, setBody] = useState('');
    const [delivery, setDelivery] = useState<DeliveryStatus | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const refresh = useCallback(async () => {
        setError(null);
        if (campaignStatus === 'DRAFT') {
            try {
                const m = await getCampaignMessage(campaignId);
                setMessage(m);
                setTitle(m.title);
                setBody(m.body);
            } catch (e) {
                if (e instanceof AuthApiError && e.status === 404) {
                    setMessage(null);
                    setTitle('');
                    setBody('');
                } else setError(errorText(e));
            }
        } else {
            setMessage(null);
            try {
                setDelivery(await getDeliveryStatus(campaignId));
            } catch (e) {
                setDelivery(null);
                setError(errorText(e));
            }
        }
    }, [campaignId, campaignStatus]);
    useEffect(() => {
        void refresh();
    }, [refresh]);

    async function save() {
        setBusy(true);
        setError(null);
        setNotice(null);
        try {
            const m = await saveCampaignMessage(campaignId, title, body);
            setMessage(m);
            setNotice('Campaign Message 已儲存。');
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    async function publish() {
        setBusy(true);
        setError(null);
        setNotice(null);
        try {
            const result = await publishCampaign(campaignId);
            setNotice(result.warningCode === 'NO_AUDIENCE_MEMBERS' ? 'Campaign 已發布，但 Audience 為 0 人。' : `Campaign 已發布，Audience ${result.audienceCount} 人，訊息正在派送。`);
            await onUpdated();
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    async function pause() {
        setBusy(true);
        setError(null);
        setNotice(null);
        try {
            await pauseCampaign(campaignId);
            setNotice('Campaign 已暫停；已提交的訊息派送會繼續。');
            await onUpdated();
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    async function retry() {
        setBusy(true);
        setError(null);
        setNotice(null);
        try {
            await retryDelivery(campaignId);
            setNotice('已提交手動重試。');
            await refresh();
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    return <section className="campaign-lifecycle">
        <div className="editor-title"><h3>Phase 7 · Message & Publication</h3><span
            className="muted">{campaignStatus}</span></div>
        {error && <p className="error-message" role="alert">{error}</p>}{notice &&
        <p className="success-message" role="status">{notice}</p>}
        {campaignStatus === 'DRAFT' && <>
            <label>In-app Message Title<input maxLength={160} required value={title}
                                              onChange={e => setTitle(e.target.value)}
                                              placeholder="最多 160 個字元"/></label>
            <label>In-app Message Body<textarea maxLength={10000} rows={5} required value={body}
                                                onChange={e => setBody(e.target.value)}
                                                placeholder="最多 10,000 個字元"/></label>
            <div className="editor-actions">
                <button type="button" disabled={busy || !title.trim() || !body.trim()} onClick={() => void save()}>儲存
                    Message
                </button>
                <button type="button" disabled={busy || !message} onClick={() => void publish()}>Publish Campaign
                </button>
            </div>
            <p className="muted">發布前必須有一則 Message、至少一張正數量 Coupon，且目前時間位於 Campaign 有效期間內。</p>
        </>}
        {campaignStatus === 'ACTIVE' && <div className="editor-actions">
            <button type="button" className="secondary" disabled={busy} onClick={() => void pause()}>Pause Campaign
            </button>
        </div>}
        {(campaignStatus === 'ACTIVE' || campaignStatus === 'PAUSED' || campaignStatus === 'ENDED') &&
            <div className="delivery-panel">
                <div className="editor-title"><h4>Message Delivery</h4>
                    <button type="button" className="secondary" disabled={busy}
                            onClick={() => void refresh()}>重新整理
                    </button>
                </div>
                {delivery ? <>
                    <div className="delivery-stats">
                        <span><strong>{delivery.status}</strong><small>狀態</small></span><span><strong>{delivery.processedCount}/{delivery.totalCount}</strong><small>已派送 / 目標</small></span><span><strong>{delivery.attemptCount}/3</strong><small>自動重試次數</small></span>
                    </div>
                    {delivery.lastError && <p className="error-message">{delivery.lastError}</p>}
                    {delivery.retryable &&
                        <button type="button" disabled={busy} onClick={() => void retry()}>Retry Failed
                            Delivery</button>}
                </> : <p className="muted">尚未取得派送狀態。</p>}
                <p className="muted">派送使用不可變 Audience Snapshot；Pause 不會取消已提交的派送工作。</p>
            </div>}
        {campaignStatus !== 'DRAFT' && campaignStatus !== 'ACTIVE' && campaignStatus !== 'PAUSED' &&
            <p className="muted">此 Campaign 目前不支援 Phase 7 操作。</p>}
    </section>;
}
