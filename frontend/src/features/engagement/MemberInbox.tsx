import {useEffect, useRef, useState} from 'react';
import {getMemberInbox, type MemberMessage, recordCampaignEvent} from '../../shared/lib/engagementApi';

function InboxCard({message}: { message: MemberMessage }) {
    const element = useRef<HTMLElement | null>(null);
    const impressionSent = useRef(false);
    const [opening, setOpening] = useState(false);
    useEffect(() => {
        const node = element.current;
        if (!node || typeof IntersectionObserver === 'undefined') return;
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting && entry.intersectionRatio >= 0.5) && !impressionSent.current) {
                impressionSent.current = true;
                void recordCampaignEvent(message.campaignId, 'IMPRESSION', message.id).catch(() => undefined);
                observer.disconnect();
            }
        }, {threshold: 0.5});
        observer.observe(node);
        return () => observer.disconnect();
    }, [message.campaignId, message.id]);

    async function openCampaign() {
        if (opening) return;
        setOpening(true);
        try {
            // Do not navigate away while the click event is still waiting for CSRF
            // initialization or the event API response. This prevents the browser
            // from unloading the page before the analytics request is dispatched.
            await recordCampaignEvent(message.campaignId, 'CLICK', message.id);
        } catch (error) {
            // Navigation must remain available even if analytics collection fails.
            console.error('Unable to record campaign click:', error);
        } finally {
            window.location.assign(`/campaigns/${message.campaignId}`);
        }
    }

    return <article className="inbox-card" ref={(node) => {
        element.current = node;
    }}>
        <div><p
            className="card-label">{message.campaignName} · {new Date(message.deliveredAt).toLocaleString('zh-TW')}</p>
            <h3>{message.title}</h3><p>{message.body}</p></div>
        <button type="button" onClick={() => void openCampaign()}
                disabled={opening}>{opening ? '開啟中…' : '查看活動'}</button>
    </article>;
}

export function MemberInbox() {
    const [messages, setMessages] = useState<MemberMessage[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    useEffect(() => {
        getMemberInbox().then(result => setMessages(result.data)).catch(cause => setError(cause instanceof Error ? cause.message : '無法載入訊息。'))
            .finally(() => setLoading(false));
    }, []);
    return <section className="experience-shell">
        <div className="experience-heading"><h2>會員訊息匣</h2><span>{messages.length} 則</span></div>
        {loading && <p>正在載入訊息…</p>}{error && <p className="error-message" role="alert">{error}</p>}
        {!loading && !error && messages.length === 0 && <p className="muted">目前沒有已送達的活動訊息。</p>}
        {messages.map(message => <InboxCard key={message.id} message={message}/>)}
    </section>;
}
