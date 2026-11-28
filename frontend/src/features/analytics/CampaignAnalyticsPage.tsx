import {useCallback, useEffect, useRef, useState} from 'react';
import {AuthApiError} from '../../shared/lib/authApi';
import {getCampaignAnalytics} from '../../shared/lib/analyticsApi';
import type {AnalyticsRate, CampaignAnalytics} from '../../shared/types/analytics';

function Rate({rate, label}: { rate: AnalyticsRate; label?: string }) {
    return <span className="analytics-rate">{label &&
        <small>{label}</small>}{rate.value === null ? '不可用（分母為 0）' : `${(rate.value * 100).toFixed(1)}%`}
        <small>{rate.numerator.toLocaleString()} / {rate.denominator.toLocaleString()}</small></span>;
}

function Metric({label, value, detail, rate, rateLabel}: {
    label: string;
    value: number;
    detail?: string;
    rate?: AnalyticsRate;
    rateLabel?: string
}) {
    return <article className="analytics-metric"><span>{label}</span><strong>{value.toLocaleString()}</strong>
        {detail && <small>{detail}</small>}{rate && <Rate rate={rate} label={rateLabel}/>}</article>;
}

export function CampaignAnalyticsPage({campaignId}: { campaignId: string }) {
    const [report, setReport] = useState<CampaignAnalytics | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [status, setStatus] = useState<number | null>(null);
    // Only the most recently started request may update this page. This also
    // invalidates in-flight requests when the campaign changes or unmounts.
    const requestVersion = useRef(0);
    // Effects run after render. Hide a previous campaign's report immediately
    // when campaignId changes, before the next request's effect starts.
    const visibleReport = report?.campaignId === campaignId ? report : null;
    const load = useCallback(async () => {
        const currentRequest = ++requestVersion.current;
        setLoading(true);
        setReport(null);
        setError(null);
        setStatus(null);
        try {
            const nextReport = await getCampaignAnalytics(campaignId);
            if (currentRequest === requestVersion.current) {
                setReport(nextReport);
            }
        } catch (e) {
            if (currentRequest === requestVersion.current) {
                setReport(null);
                setError(e instanceof Error ? e.message : '無法載入活動報表。');
                setStatus(e instanceof AuthApiError ? e.status : null);
            }
        } finally {
            if (currentRequest === requestVersion.current) {
                setLoading(false);
            }
        }
    }, [campaignId]);
    useEffect(() => {
        void load();
        return () => {
            // Invalidate the request on campaign change and component unmount.
            requestVersion.current += 1;
        };
    }, [load]);

    return <section className="analytics-shell">
        <div className="analytics-heading">
            <div><p className="eyebrow">PHASE 9 · CAMPAIGN ANALYTICS</p>
                <h2>Campaign Analytics</h2><p className="muted">活動生命週期累計數據；事件數與不重複會員數分開呈現。</p>
            </div>
            <a className="secondary-link" href="/admin/campaigns">返回 Campaigns</a></div>
        {loading && <p className="notice-message" role="status">正在載入活動分析…</p>}
        {!loading && error && <div className="error-message" role="alert">
            <p>{status === 404 ? '找不到此 Campaign。' : status === 401 || status === 403 ? '此報表僅供管理員檢視。' : error}</p>
            <button type="button" onClick={() => void load()}>重新載入</button>
        </div>}
        {!loading && visibleReport && <>
            <div className="analytics-heading">
                <div><h3>{visibleReport.campaignName}</h3><small className="muted">{visibleReport.campaignId}</small>
                </div>
                <button type="button" className="secondary" onClick={() => void load()}>重新整理</button>
            </div>
            {visibleReport.deliveredMembers === 0 && visibleReport.impressionCount === 0 && visibleReport.clickCount === 0
                && visibleReport.landingPageViews === 0 && visibleReport.couponClaims === 0 && visibleReport.claimAttemptCount === 0
                && <p className="notice-message">此活動目前尚無送達、互動或領券紀錄。</p>}
            <h3>Audience & Delivery</h3>
            <div className="analytics-grid">
                <Metric label="Target Members" value={visibleReport.targetMembers}/>
                <Metric label="Delivered Members" value={visibleReport.deliveredMembers}
                        rate={visibleReport.deliveryRate}
                        rateLabel="Delivery Rate"/>
            </div>
            <h3>Message Engagement</h3>
            <div className="analytics-grid">
                <Metric label="Impression Count" value={visibleReport.impressionCount}/><Metric label="Unique Reach"
                                                                                                value={visibleReport.uniqueReach}
                                                                                                rate={visibleReport.reachRate}
                                                                                                rateLabel="Reach Rate"/>
                <Metric label="Click Count" value={visibleReport.clickCount}/><Metric label="Unique Clickers"
                                                                                      value={visibleReport.uniqueClickers}
                                                                                      rate={visibleReport.clickRate}
                                                                                      rateLabel="Click Rate"/>
            </div>
            <h3>Landing Page</h3>
            <div className="analytics-grid">
                <Metric label="Landing Page Views" value={visibleReport.landingPageViews}/>
                <Metric label="Unique Landing Visitors" value={visibleReport.uniqueLandingVisitors}
                        rate={visibleReport.targetLandingVisitorRate} rateLabel="Target Landing Visitor Rate"/>
            </div>
            <h3>Coupon Claims & Audit</h3>
            <div className="analytics-grid">
                <Metric label="Successful Coupon Claims" value={visibleReport.couponClaims}/>
                <Metric label="Unique Claimers" value={visibleReport.uniqueClaimers}
                        rate={visibleReport.targetClaimRate}
                        rateLabel="Target Claim Rate"/>
                <Metric label="Claim Attempts" value={visibleReport.claimAttemptCount}
                        detail={`成功 ${visibleReport.claimAttemptsByOutcome.succeeded} · 失敗 ${visibleReport.claimAttemptsByOutcome.failed} · 重播 ${visibleReport.claimAttemptsByOutcome.replayed}`}/>
                <Metric label="Failed Claim Attempts" value={visibleReport.failedClaimAttemptCount}/>
            </div>
            <h3>Coupon Performance</h3>
            {visibleReport.coupons.length === 0 ? <p className="muted">此 Campaign 尚未設定優惠券。</p> :
                <div className="analytics-table-wrap">
                    <table className="analytics-table">
                        <thead>
                        <tr>
                            <th>Coupon</th>
                            <th>Configured</th>
                            <th>Successful Claims</th>
                            <th>Remaining</th>
                            <th>Unique Claimers</th>
                            <th>Target Claim Rate</th>
                        </tr>
                        </thead>
                        <tbody>{visibleReport.coupons.map(c => <tr key={c.couponId}>
                            <td><strong>{c.name}</strong><small>{c.couponId}</small></td>
                            <td>{c.totalQuantity}</td>
                            <td>{c.successfulClaims}</td>
                            <td>{c.remainingQuantity}</td>
                            <td>{c.uniqueClaimers}</td>
                            <td><Rate rate={c.targetClaimRate}/></td>
                        </tr>)}</tbody>
                    </table>
                </div>}
            {visibleReport.targetMembers === 0 &&
                <p className="notice-message">此活動目標會員數為 0；以目標會員為分母的比率顯示為不可用。</p>}
            <p className="analytics-footnote">所有指標皆為活動累計值。Landing Visitor Rate
                為目標受眾滲透率，非點擊歸因或轉換率。</p>
        </>}
    </section>;
}
