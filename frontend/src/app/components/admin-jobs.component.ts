import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../services/api.service';

interface JobStatus {
  jobName: string;
  displayName: string;
  schedule: string;
  lastStatus: string;
  lastRunAt: string | null;
  lastSuccessAt: string | null;
  lastErrorMessage: string | null;
}

interface DailyScore {
  scoreDate: string;
  predictionType: string;
  totalResolved: number;
  successCount: number;
  failureCount: number;
  successRatePct: number;
  avgAbsoluteError: number | null;
  avgPercentageError: number | null;
}

interface ScoresByType {
  combined:    DailyScore[];
  hourlyPrice: DailyScore[];
  swingTrade:  DailyScore[];
  trend:       DailyScore[];
}

interface ChartPoint { cx: number; cy: number; title: string; }
interface XLabel     { show: boolean; x: number; y: number; text: string; }
interface GridLine   { y: number; pct: number; }

interface ChartData {
  w: number; h: number;
  linePoints: string;
  areaPoints: string;
  gridLines: GridLine[];
  dots: ChartPoint[];
  labels: XLabel[];
  padL: number; padT: number; padB: number;
  latest: DailyScore | null;
  avgRate: string;
  totalScored: number;
  hasData: boolean;
  color: string;
  fillColor: string;
}

interface TypeChartConfig {
  title: string;
  subtitle: string;
  data: ChartData;
}

interface SwingWeight {
  strategyName: string;
  weight: number;
  winCount: number;
  lossCount: number;
  lastUpdated: string;
}

interface TrendChange {
  id: number;
  symbol: string;
  technique: string;
  previousWeight: number;
  newWeight: number;
  changedAt: string;
}

interface WeightChanges {
  swingWeights: SwingWeight[];
  recentTrendChanges: TrendChange[];
}

@Component({
  selector: 'app-admin-jobs',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="jobs-container">
      <h2>Scheduled Jobs</h2>

      <!-- Job cards -->
      <div class="job-grid">
        <div *ngFor="let job of jobs" class="job-card">
          <div class="job-header">
            <span class="job-name">{{ job.displayName }}</span>
            <span class="badge" [ngClass]="badgeClass(job.lastStatus)">{{ job.lastStatus }}</span>
          </div>
          <div class="job-meta">
            <span class="schedule">Schedule: {{ job.schedule }}</span>
          </div>
          <div class="job-info">
            <div *ngIf="job.lastRunAt" class="info-row">
              <span class="label">Last Run:</span>
              <span>{{ job.lastRunAt | date:'MMM d, y HH:mm' }}</span>
            </div>
            <div *ngIf="job.lastSuccessAt" class="info-row">
              <span class="label">Last Success:</span>
              <span>{{ job.lastSuccessAt | date:'MMM d, y HH:mm' }}</span>
            </div>
            <div *ngIf="job.lastErrorMessage" class="error-msg">{{ job.lastErrorMessage }}</div>
            <div *ngIf="!job.lastRunAt" class="info-row muted">Never executed</div>
          </div>
          <button class="btn-trigger"
                  [disabled]="triggeringJob === job.jobName"
                  (click)="triggerJob(job)">
            {{ triggeringJob === job.jobName ? 'Running...' : 'Trigger Now' }}
          </button>
          <div *ngIf="triggerMessages[job.jobName]" class="trigger-msg"
               [ngClass]="triggerMessages[job.jobName].ok ? 'msg-ok' : 'msg-err'">
            {{ triggerMessages[job.jobName].text }}
          </div>
        </div>
      </div>

      <!-- Combined prediction accuracy chart -->
      <section class="section">
        <h3>Combined Prediction Accuracy
          <span class="subtitle">(all prediction types - 90-day window)</span>
        </h3>
        <div *ngIf="!combinedChart.hasData" class="empty">No scoring data available yet.</div>
        <div *ngIf="combinedChart.hasData" class="chart-wrapper">
          <svg [attr.viewBox]="'0 0 ' + combinedChart.w + ' ' + combinedChart.h"
               class="chart-svg" preserveAspectRatio="xMidYMid meet">
            <g *ngFor="let gl of combinedChart.gridLines">
              <line [attr.x1]="combinedChart.padL" [attr.x2]="combinedChart.w - 12"
                    [attr.y1]="gl.y" [attr.y2]="gl.y" stroke="#e0e0e0" stroke-width="1"/>
              <text [attr.x]="combinedChart.padL - 6" [attr.y]="gl.y + 4"
                    text-anchor="end" font-size="10" fill="#888">{{ gl.pct }}%</text>
            </g>
            <polygon [attr.points]="combinedChart.areaPoints" [attr.fill]="combinedChart.fillColor"/>
            <polyline [attr.points]="combinedChart.linePoints" fill="none"
                      [attr.stroke]="combinedChart.color" stroke-width="2.5"
                      stroke-linejoin="round" stroke-linecap="round"/>
            <g *ngFor="let dot of combinedChart.dots">
              <title>{{ dot.title }}</title>
              <circle [attr.cx]="dot.cx" [attr.cy]="dot.cy" r="4"
                      [attr.fill]="combinedChart.color" stroke="white" stroke-width="1.5" class="dot"/>
            </g>
            <g *ngFor="let lbl of combinedChart.labels">
              <text *ngIf="lbl.show" [attr.x]="lbl.x" [attr.y]="lbl.y"
                    text-anchor="middle" font-size="9" fill="#888">{{ lbl.text }}</text>
            </g>
            <line [attr.x1]="combinedChart.padL" [attr.x2]="combinedChart.padL"
                  [attr.y1]="combinedChart.padT" [attr.y2]="combinedChart.h - combinedChart.padB"
                  stroke="#aaa" stroke-width="1"/>
            <line [attr.x1]="combinedChart.padL" [attr.x2]="combinedChart.w - 12"
                  [attr.y1]="combinedChart.h - combinedChart.padB"
                  [attr.y2]="combinedChart.h - combinedChart.padB"
                  stroke="#aaa" stroke-width="1"/>
          </svg>
          <div class="chart-stats">
            <span>Latest: <strong>{{ combinedChart.latest?.successRatePct }}%</strong></span>
            <span>30-day avg: <strong>{{ combinedChart.avgRate }}%</strong></span>
            <span>Total predictions: <strong>{{ combinedChart.totalScored }}</strong></span>
          </div>
        </div>
      </section>

      <!-- Per-type accuracy charts -->
      <section class="section">
        <h3>Accuracy by Prediction Type</h3>
        <div class="type-charts-grid">
          <div *ngFor="let tc of typeCharts" class="type-chart-panel">
            <h4>{{ tc.title }} <span class="subtitle">{{ tc.subtitle }}</span></h4>
            <div *ngIf="!tc.data.hasData" class="empty small">No data yet for this period.</div>
            <ng-container *ngIf="tc.data.hasData">
              <svg [attr.viewBox]="'0 0 ' + tc.data.w + ' ' + tc.data.h"
                   class="chart-svg" preserveAspectRatio="xMidYMid meet">
                <g *ngFor="let gl of tc.data.gridLines">
                  <line [attr.x1]="tc.data.padL" [attr.x2]="tc.data.w - 12"
                        [attr.y1]="gl.y" [attr.y2]="gl.y" stroke="#e0e0e0" stroke-width="1"/>
                  <text [attr.x]="tc.data.padL - 6" [attr.y]="gl.y + 4"
                        text-anchor="end" font-size="10" fill="#888">{{ gl.pct }}%</text>
                </g>
                <polygon [attr.points]="tc.data.areaPoints" [attr.fill]="tc.data.fillColor"/>
                <polyline [attr.points]="tc.data.linePoints" fill="none"
                          [attr.stroke]="tc.data.color" stroke-width="2"
                          stroke-linejoin="round" stroke-linecap="round"/>
                <g *ngFor="let dot of tc.data.dots">
                  <title>{{ dot.title }}</title>
                  <circle [attr.cx]="dot.cx" [attr.cy]="dot.cy" r="3.5"
                          [attr.fill]="tc.data.color" stroke="white" stroke-width="1.5" class="dot"/>
                </g>
                <g *ngFor="let lbl of tc.data.labels">
                  <text *ngIf="lbl.show" [attr.x]="lbl.x" [attr.y]="lbl.y"
                        text-anchor="middle" font-size="9" fill="#888">{{ lbl.text }}</text>
                </g>
                <line [attr.x1]="tc.data.padL" [attr.x2]="tc.data.padL"
                      [attr.y1]="tc.data.padT" [attr.y2]="tc.data.h - tc.data.padB"
                      stroke="#aaa" stroke-width="1"/>
                <line [attr.x1]="tc.data.padL" [attr.x2]="tc.data.w - 12"
                      [attr.y1]="tc.data.h - tc.data.padB"
                      [attr.y2]="tc.data.h - tc.data.padB"
                      stroke="#aaa" stroke-width="1"/>
              </svg>
              <div class="chart-stats small">
                <span>Latest: <strong [attr.style]="'color:' + tc.data.color">{{ tc.data.latest?.successRatePct }}%</strong></span>
                <span>30-day avg: <strong>{{ tc.data.avgRate }}%</strong></span>
                <span>Total: <strong>{{ tc.data.totalScored }}</strong></span>
              </div>
            </ng-container>
          </div>
        </div>
      </section>

      <!-- Weight changes -->
      <section class="section">
        <h3>Strategy Weights</h3>
        <div class="weight-panels">
          <!-- Swing weights -->
          <div class="weight-panel">
            <h4>Swing Strategy Weights</h4>
            <table *ngIf="weightChanges?.swingWeights?.length; else noSwing">
              <thead>
                <tr><th>Strategy</th><th>Weight</th><th>Wins</th><th>Losses</th><th>Updated</th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let w of weightChanges?.swingWeights">
                  <td>{{ w.strategyName }}</td>
                  <td><div class="bar-cell">
                    <div class="bar" [style.width.%]="w.weight * 100"></div>
                    <span>{{ (w.weight * 100).toFixed(1) }}%</span>
                  </div></td>
                  <td class="win">{{ w.winCount }}</td>
                  <td class="loss">{{ w.lossCount }}</td>
                  <td class="muted small">{{ w.lastUpdated | date:'MMM d' }}</td>
                </tr>
              </tbody>
            </table>
            <ng-template #noSwing><p class="empty">No swing weight data.</p></ng-template>
          </div>

          <!-- Trend weight history -->
          <div class="weight-panel">
            <h4>Recent Trend Weight Changes</h4>
            <table *ngIf="weightChanges?.recentTrendChanges?.length; else noTrend">
              <thead>
                <tr><th>Symbol</th><th>Technique</th><th>From</th><th>To</th><th>When</th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let t of weightChanges?.recentTrendChanges">
                  <td><strong>{{ t.symbol }}</strong></td>
                  <td class="small">{{ t.technique }}</td>
                  <td class="muted">{{ (+(t.previousWeight) * 100).toFixed(1) }}%</td>
                  <td [ngClass]="t.newWeight > t.previousWeight ? 'win' : 'loss'">
                    {{ (+(t.newWeight) * 100).toFixed(1) }}%
                    {{ t.newWeight > t.previousWeight ? 'up' : 'down' }}
                  </td>
                  <td class="muted small">{{ t.changedAt | date:'MMM d HH:mm' }}</td>
                </tr>
              </tbody>
            </table>
            <ng-template #noTrend><p class="empty">No trend weight history.</p></ng-template>
          </div>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .jobs-container { padding: 24px; max-width: 1200px; margin: 0 auto; }
    h2 { margin-bottom: 20px; color: #1a237e; }
    h3 { color: #283593; margin-bottom: 12px; }
    h4 { color: #37474f; margin-bottom: 10px; }
    .subtitle { font-size: 0.75rem; color: #888; font-weight: normal; }

    /* Job grid */
    .job-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; margin-bottom: 32px; }
    .job-card { background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 16px; display: flex; flex-direction: column; gap: 8px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
    .job-header { display: flex; justify-content: space-between; align-items: center; }
    .job-name { font-weight: 600; font-size: 0.95rem; color: #1a237e; }
    .schedule { font-size: 0.8rem; color: #78909c; }
    .info-row { display: flex; gap: 6px; font-size: 0.8rem; }
    .label { color: #90a4ae; }
    .error-msg { font-size: 0.75rem; color: #c62828; background: #ffebee; padding: 4px 6px; border-radius: 4px; }
    .muted { color: #90a4ae; }
    .small { font-size: 0.78rem; }
    .btn-trigger { margin-top: 8px; padding: 7px 14px; background: #1976d2; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 0.85rem; transition: background 0.2s; }
    .btn-trigger:hover:not(:disabled) { background: #1565c0; }
    .btn-trigger:disabled { background: #90caf9; cursor: not-allowed; }
    .trigger-msg { font-size: 0.78rem; padding: 4px 6px; border-radius: 4px; }
    .msg-ok { background: #e8f5e9; color: #2e7d32; }
    .msg-err { background: #ffebee; color: #c62828; }

    /* Badges */
    .badge { font-size: 0.72rem; padding: 2px 8px; border-radius: 10px; font-weight: 600; text-transform: uppercase; }
    .badge-success { background: #e8f5e9; color: #2e7d32; }
    .badge-failed  { background: #ffebee; color: #c62828; }
    .badge-running { background: #e3f2fd; color: #1565c0; }
    .badge-never   { background: #f5f5f5; color: #9e9e9e; }
    .badge-unknown { background: #fff3e0; color: #e65100; }

    /* Sections */
    .section { margin-bottom: 36px; }
    .empty { color: #90a4ae; font-style: italic; padding: 12px 0; }

    /* Charts */
    .chart-wrapper { background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 16px; }
    .chart-svg { width: 100%; height: auto; display: block; }
    .dot { cursor: pointer; }
    .chart-stats { display: flex; gap: 24px; margin-top: 8px; font-size: 0.85rem; color: #546e7a; padding-top: 8px; border-top: 1px solid #f0f0f0; flex-wrap: wrap; }
    .chart-stats.small { font-size: 0.78rem; gap: 12px; }

    /* Per-type chart grid */
    .type-charts-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    @media (max-width: 900px) { .type-charts-grid { grid-template-columns: 1fr; } }
    .type-chart-panel { background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 14px; }
    .type-chart-panel h4 { margin-bottom: 8px; font-size: 0.9rem; }

    /* Weight tables */
    .weight-panels { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
    @media (max-width: 700px) { .weight-panels { grid-template-columns: 1fr; } }
    .weight-panel { background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 16px; }
    table { width: 100%; border-collapse: collapse; font-size: 0.83rem; }
    th { text-align: left; padding: 6px 8px; background: #f5f5f5; color: #546e7a; border-bottom: 1px solid #e0e0e0; }
    td { padding: 6px 8px; border-bottom: 1px solid #f0f0f0; }
    .win  { color: #2e7d32; font-weight: 600; }
    .loss { color: #c62828; font-weight: 600; }
    .bar-cell { display: flex; align-items: center; gap: 6px; }
    .bar { height: 8px; background: #90caf9; border-radius: 4px; min-width: 2px; }
  `]
})
export class AdminJobsComponent implements OnInit {

  jobs: JobStatus[] = [];
  scoresByType: ScoresByType | null = null;
  weightChanges: WeightChanges | null = null;
  triggeringJob: string | null = null;
  triggerMessages: Record<string, { ok: boolean; text: string }> = {};

  constructor(private api: ApiService, private cd: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.api.getJobStatuses().subscribe({ next: d => { this.jobs = d; this.cd.markForCheck(); } });
    this.api.getPredictionScores().subscribe({ next: d => { this.scoresByType = d as unknown as ScoresByType; this.cd.markForCheck(); } });
    this.api.getWeightChanges().subscribe({ next: d => { this.weightChanges = d; this.cd.markForCheck(); } });
  }

  // Chart data

  get combinedChart(): ChartData {
    return this.buildChart(this.scoresByType?.combined ?? [], 840, 260, '#1976d2');
  }

  get typeCharts(): TypeChartConfig[] {
    return [
      { title: 'Hourly Price',  subtitle: '+/-3% of actual = success',
        data: this.buildChart(this.scoresByType?.hourlyPrice ?? [], 400, 210, '#2e7d32') },
      { title: 'Swing Trade',   subtitle: 'trade resolved correctly = success',
        data: this.buildChart(this.scoresByType?.swingTrade  ?? [], 400, 210, '#e65100') },
      { title: 'Trend',         subtitle: 'correct bull/bear direction = success',
        data: this.buildChart(this.scoresByType?.trend        ?? [], 400, 210, '#6a1b9a') },
    ];
  }

  private buildChart(scores: DailyScore[], w: number, h: number, color: string): ChartData {
    const padL = 44, padR = 12, padT = 12, padB = 34;
    const plotW = w - padL - padR;
    const plotH = h - padT - padB;
    const n = scores.length;
    const xScale = (i: number) => n <= 1 ? padL : padL + (i / (n - 1)) * plotW;
    const yScale = (pct: number) => padT + (1 - pct / 100) * plotH;
    const baseline = h - padB;

    const linePoints = scores.map((s, i) => `${xScale(i).toFixed(1)},${yScale(s.successRatePct).toFixed(1)}`).join(' ');
    const areaPoints = n === 0 ? '' :
      `${padL},${baseline} ${linePoints} ${xScale(n - 1).toFixed(1)},${baseline}`;

    const step = Math.max(1, Math.floor(n / 7));
    const labels: XLabel[] = scores.map((s, i) => ({
      show: n <= 1 || i % step === 0 || i === n - 1,
      x: xScale(i), y: h - padB + 13,
      text: s.scoreDate?.length >= 10 ? s.scoreDate.substring(5, 10) : (s.scoreDate ?? '')
    }));

    const dots: ChartPoint[] = scores.map((s, i) => ({
      cx: xScale(i), cy: yScale(s.successRatePct),
      title: `${s.scoreDate}: ${s.successRatePct}% (${s.successCount}/${s.totalResolved})`
    }));

    const gridLines: GridLine[] = [0, 25, 50, 75, 100].map(pct => ({ y: yScale(pct), pct }));
    const latest = n > 0 ? scores[n - 1] : null;
    const slice = scores.slice(-30);
    const avgRate = slice.length === 0 ? '-'
      : (slice.reduce((sum, s) => sum + s.successRatePct, 0) / slice.length).toFixed(1);
    const totalScored = scores.reduce((sum, s) => sum + s.totalResolved, 0);

    // Derive a semi-transparent fill from the hex color
    const rgb = parseInt(color.slice(1), 16);
    const r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
    const fillColor = `rgba(${r},${g},${b},0.10)`;

    return { w, h, linePoints, areaPoints, gridLines, dots, labels,
             padL, padT, padB, latest, avgRate, totalScored, hasData: n > 0, color, fillColor };
  }

  // Job actions

  triggerJob(job: JobStatus): void {
    this.triggeringJob = job.jobName;
    delete this.triggerMessages[job.jobName];
    this.api.triggerJob(job.jobName).subscribe({
      next: (res) => {
        const isAsync = res?.status === 'accepted';
        this.triggerMessages[job.jobName] = {
          ok: true,
          text: isAsync
            ? 'Job started in background. Refresh in a moment to see the result.'
            : 'Triggered successfully. Refresh in a moment.'
        };
        this.triggeringJob = null;
        this.cd.markForCheck();
        setTimeout(() => {
          this.api.getJobStatuses().subscribe({ next: d => { this.jobs = d; this.cd.markForCheck(); } });
        }, 3000);
      },
      error: (err) => {
        this.triggerMessages[job.jobName] = { ok: false, text: err?.error?.error ?? 'Trigger failed.' };
        this.triggeringJob = null;
        this.cd.markForCheck();
      }
    });
  }

  badgeClass(status: string): string {
    const map: Record<string, string> = {
      SUCCESS: 'badge-success', FAILED: 'badge-failed', RUNNING: 'badge-running', NEVER: 'badge-never'
    };
    return map[status] ?? 'badge-unknown';
  }
}

