import {useCallback, useEffect, useState} from 'react';
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
    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        setStatus(null);
        try {
            setReport(await getCampaignAnalytics(campaignId));
        } catch (e) {
            setReport(null);
            setError(e instanceof Error ? e.message : '無法載入活動報表。');
            setStatus(e instanceof AuthApiError ? e.status : null);
        } finally {
            setLoading(false);
        }
    }, [campaignId]);
    useEffect(() => {
        void load();
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
        {!loading && report && <>
            <div className="analytics-heading">
                <div><h3>{report.campaignName}</h3><small className="muted">{report.campaignId}</small></div>
                <button type="button" className="secondary" onClick={() => void load()}>重新整理</button>
            </div>
            {report.deliveredMembers === 0 && report.impressionCount === 0 && report.clickCount === 0
                && report.landingPageViews === 0 && report.couponClaims === 0 && report.claimAttemptCount === 0
                && <p className="notice-message">此活動目前尚無送達、互動或領券紀錄。</p>}
            <h3>Audience & Delivery</h3>
            <div className="analytics-grid">
                <Metric label="Target Members" value={report.targetMembers}/>
                <Metric label="Delivered Members" value={report.deliveredMembers} rate={report.deliveryRate}
                        rateLabel="Delivery Rate"/>
            </div>
            <h3>Message Engagement</h3>
            <div className="analytics-grid">
                <Metric label="Impression Count" value={report.impressionCount}/><Metric label="Unique Reach"
                                                                                         value={report.uniqueReach}
                                                                                         rate={report.reachRate}
                                                                                         rateLabel="Reach Rate"/>
                <Metric label="Click Count" value={report.clickCount}/><Metric label="Unique Clickers"
                                                                               value={report.uniqueClickers}
                                                                               rate={report.clickRate}
                                                                               rateLabel="Click Rate"/>
            </div>
            <h3>Landing Page</h3>
            <div className="analytics-grid">
                <Metric label="Landing Page Views" value={report.landingPageViews}/>
                <Metric label="Unique Landing Visitors" value={report.uniqueLandingVisitors}
                        rate={report.targetLandingVisitorRate} rateLabel="Target Landing Visitor Rate"/>
            </div>
            <h3>Coupon Claims & Audit</h3>
            <div className="analytics-grid">
                <Metric label="Successful Coupon Claims" value={report.couponClaims}/>
                <Metric label="Unique Claimers" value={report.uniqueClaimers} rate={report.targetClaimRate}
                        rateLabel="Target Claim Rate"/>
                <Metric label="Claim Attempts" value={report.claimAttemptCount}
                        detail={`成功 ${report.claimAttemptsByOutcome.succeeded} · 失敗 ${report.claimAttemptsByOutcome.failed} · 重播 ${report.claimAttemptsByOutcome.replayed}`}/>
                <Metric label="Failed Claim Attempts" value={report.failedClaimAttemptCount}/>
            </div>
            <h3>Coupon Performance</h3>
            {report.coupons.length === 0 ? <p className="muted">此 Campaign 尚未設定優惠券。</p> :
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
                        <tbody>{report.coupons.map(c => <tr key={c.couponId}>
                            <td><strong>{c.name}</strong><small>{c.couponId}</small></td>
                            <td>{c.totalQuantity}</td>
                            <td>{c.successfulClaims}</td>
                            <td>{c.remainingQuantity}</td>
                            <td>{c.uniqueClaimers}</td>
                            <td><Rate rate={c.targetClaimRate}/></td>
                        </tr>)}</tbody>
                    </table>
                </div>}
            {report.targetMembers === 0 &&
                <p className="notice-message">此活動目標會員數為 0；以目標會員為分母的比率顯示為不可用。</p>}
            <p className="analytics-footnote">所有指標皆為活動累計值。Landing Visitor Rate
                為目標受眾滲透率，非點擊歸因或轉換率。</p>
        </>}
    </section>;
}
