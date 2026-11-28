import {useEffect, useRef, useState} from 'react';
import {AuthApiError} from '../../shared/lib/authApi';
import {useAuth} from '../auth/AuthProvider';
import {
    claimCampaignCoupon,
    getPublicCampaign,
    getPublicCoupons,
    type LandingCampaign,
    type PublicCoupon,
    recordCampaignEvent
} from '../../shared/lib/engagementApi';

const errorMessage = (error: unknown) => error instanceof AuthApiError
    ? error.message : error instanceof Error ? error.message : '目前無法載入活動，請稍後重試。';
const dateTime = (value: string) => new Date(value).toLocaleString('zh-TW', {dateStyle: 'medium', timeStyle: 'short'});

export function CampaignLandingPage({campaignId}: { campaignId: string }) {
    const {user} = useAuth();
    const [campaign, setCampaign] = useState<LandingCampaign | null>(null);
    const [coupons, setCoupons] = useState<PublicCoupon[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [claiming, setClaiming] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [claimError, setClaimError] = useState<string | null>(null);
    const claimKeys = useRef(new Map<string, string>());
    const viewRecorded = useRef<string | null>(null);
    const requestSequence = useRef(0);

    async function refresh() {
        const requestId = ++requestSequence.current;
        setLoading(true);
        setError(null);
        try {
            const [page, couponPage] = await Promise.all([getPublicCampaign(campaignId), getPublicCoupons(campaignId)]);
            if (requestId !== requestSequence.current) return;
            setCampaign(page);
            setCoupons(couponPage.data);
            couponPage.data.filter(coupon => coupon.memberStatus === 'ALREADY_CLAIMED').forEach(coupon => {
                claimKeys.current.delete(coupon.id);
                try {
                    sessionStorage.removeItem(`phase8:claim:${campaignId}:${coupon.id}`);
                } catch { /* Ignore unavailable storage. */
                }
            });
            if (user?.role === 'MEMBER' && viewRecorded.current !== campaignId) {
                viewRecorded.current = campaignId;
                void recordCampaignEvent(campaignId, 'LANDING_PAGE_VIEW').catch(() => undefined);
            }
        } catch (cause) {
            if (requestId === requestSequence.current) setError(errorMessage(cause));
        } finally {
            if (requestId === requestSequence.current) setLoading(false);
        }
    }

    useEffect(() => {
        void refresh();
        return () => {
            requestSequence.current += 1;
        };
    }, [campaignId, user?.role]);

    function beginLogin() {
        const returnTo = `/campaigns/${campaignId}`;
        window.location.assign(`/login?returnTo=${encodeURIComponent(returnTo)}`);
    }

    async function claim(coupon: PublicCoupon) {
        if (user?.role !== 'MEMBER') {
            beginLogin();
            return;
        }
        setClaiming(coupon.id);
        setNotice(null);
        setClaimError(null);
        const storageKey = `phase8:claim:${campaignId}:${coupon.id}`;
        let storedKey: string | null = null;
        try {
            storedKey = sessionStorage.getItem(storageKey);
        } catch { /* Storage may be disabled. */
        }
        const key = claimKeys.current.get(coupon.id) ?? storedKey ?? crypto.randomUUID();
        claimKeys.current.set(coupon.id, key);
        try {
            sessionStorage.setItem(storageKey, key);
        } catch { /* Keep the in-memory idempotency key. */
        }
        try {
            const result = await claimCampaignCoupon(campaignId, coupon.id, key);
            claimKeys.current.delete(coupon.id);
            try {
                sessionStorage.removeItem(storageKey);
            } catch { /* Ignore unavailable storage. */
            }
            setNotice(`領取成功！領取編號：${result.data.claimId}`);
            await refresh();
        } catch (cause) {
            setClaimError(errorMessage(cause));
        } finally {
            setClaiming(null);
        }
    }

    if (loading) return <section className="experience-shell" aria-live="polite">正在載入活動內容…</section>;
    if (error || !campaign) return <section className="experience-shell"><h2>無法顯示活動</h2><p
        className="error-message" role="alert">{error ?? '找不到此活動。'}</p>
        <button onClick={() => void refresh()}>重新載入</button>
    </section>;

    const eligible = user?.role === 'MEMBER' ? campaign.memberState?.eligible === true : null;
    return <article className="experience-shell">
        <p className="eyebrow">CAMPAIGN · {campaign.status}</p>
        <h2>{campaign.name}</h2>
        {campaign.description && <p className="experience-description">{campaign.description}</p>}
        <div className="campaign-period">
            <span>活動期間</span><strong>{dateTime(campaign.startsAt)} – {dateTime(campaign.endsAt)}</strong></div>
        <section className="landing-copy"><h3>{campaign.landingPage.headline}</h3><p>{campaign.landingPage.body}</p>
        </section>
        <section className="coupon-section">
            <div className="experience-heading"><h3>活動優惠券</h3><span>{coupons.length} 張</span></div>
            {eligible === false &&
                <p className="notice-message">此會員不在本活動發布時的目標受眾名單中，因此無法領取優惠券。</p>}
            {user?.role === 'ADMIN' &&
                <p className="notice-message">目前使用管理員帳號；請使用會員帳號登入以查看會員資格及領取優惠券。</p>}
            {notice && <p className="success-message" role="status">{notice}</p>}
            {claimError && <p className="error-message" role="alert">{claimError}</p>}
            {coupons.length === 0 ? <p className="muted">此活動目前沒有可展示的優惠券。</p> : coupons.map(coupon => {
                const statusText = coupon.memberStatus === 'ALREADY_CLAIMED' ? '已領取'
                    : coupon.memberStatus === 'NOT_ELIGIBLE' ? '不符合領取資格'
                        : coupon.memberStatus === 'AVAILABLE' ? '可領取' : '目前不可領取';
                const canClaim = coupon.memberStatus === 'AVAILABLE' && campaign.claimable && (user?.role === 'MEMBER' ? eligible === true : user == null);
                return <div className="coupon-card" key={coupon.id}>
                    <div><h4>{coupon.name}</h4>{coupon.description && <p>{coupon.description}</p>}
                        <p className="coupon-value">{coupon.discountType === 'PERCENTAGE' ? `${coupon.discountValue}% 折扣` : `$${coupon.discountValue} 折抵`}</p>
                        <small>剩餘 {coupon.remainingQuantity} / {coupon.totalQuantity} ·
                            有效期間 {dateTime(coupon.startsAt)} 至 {dateTime(coupon.endsAt)}</small>
                        {coupon.minimumOrderAmount !== null && <small>最低消費金額：${coupon.minimumOrderAmount}</small>}
                        {coupon.unavailableReason && <small
                            className="muted">{coupon.unavailableReason === 'COUPON_SOLD_OUT' ? '優惠券已額滿' : coupon.unavailableReason === 'COUPON_NOT_STARTED' ? '尚未開始' : coupon.unavailableReason === 'COUPON_EXPIRED' ? '已過有效期限' : coupon.unavailableReason === 'MEMBER_NOT_ELIGIBLE' ? '不符合活動受眾資格' : '活動目前未開放領取'}</small>}
                    </div>
                    <div className="coupon-action"><span
                        className={`coupon-status ${coupon.memberStatus.toLowerCase()}`}>{statusText}</span>
                        {canClaim && <button disabled={claiming !== null}
                                             onClick={() => void claim(coupon)}>{claiming === coupon.id ? '領取中…' : user?.role === 'MEMBER' ? '立即領取' : '登入會員領取'}</button>}
                        {coupon.memberStatus === 'AVAILABLE' && user?.role === 'MEMBER' && eligible === false &&
                            <span className="muted">無法領取</span>}
                        {coupon.memberStatus === 'AVAILABLE' && user?.role === 'ADMIN' &&
                            <span className="muted">會員限定</span>}
                    </div>
                </div>;
            })}
        </section>
        {campaign.status !== 'ACTIVE' &&
            <p className="notice-message">活動已{campaign.status === 'PAUSED' ? '暫停' : '結束'}，仍可瀏覽活動內容，但不能領取新的優惠券。</p>}
    </article>;
}
