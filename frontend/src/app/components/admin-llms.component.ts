import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, OllamaSettings } from '../services/api.service';

@Component({
  selector: 'app-admin-llms',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="admin-container">
      <div class="page-header">
        <div>
          <p class="eyebrow">Administration</p>
          <h2>LLMs</h2>
          <p class="intro">Configure the Ollama model and chat endpoint used for news analysis.</p>
        </div>
        <span class="status-pill" [class.ready]="!loading && !errorMessage">
          {{ loading ? 'Loading' : errorMessage ? 'Unavailable' : 'Ready' }}
        </span>
      </div>

      <section class="settings-panel" aria-labelledby="ollama-heading">
        <div class="panel-heading">
          <div>
            <h3 id="ollama-heading">Ollama connection</h3>
            <p>Changes apply to the next news-analysis request and are retained in the application database.</p>
          </div>
          <span class="provider-mark">OLLAMA</span>
        </div>

        <div *ngIf="loading" class="notice">Loading LLM settings...</div>
        <div *ngIf="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>

        <form *ngIf="!loading" (ngSubmit)="save()" #settingsForm="ngForm" novalidate>
          <div class="form-grid">
            <label class="form-field">
              <span>Model name</span>
              <input
                name="model"
                type="text"
                [(ngModel)]="settings.model"
                required
                maxlength="80"
                placeholder="gemma3:1b"
                autocomplete="off">
              <small>Use the exact model name installed in Ollama.</small>
            </label>

            <label class="form-field">
              <span>Ollama chat endpoint</span>
              <input
                name="endpoint"
                type="url"
                [(ngModel)]="settings.endpoint"
                required
                maxlength="500"
                placeholder="http://localhost:11434/api/chat"
                autocomplete="url">
              <small>Default: http://localhost:11434/api/chat</small>
            </label>
          </div>

          <div class="form-footer">
            <span *ngIf="successMessage" class="notice success" role="status">{{ successMessage }}</span>
            <span *ngIf="saveError" class="notice error" role="alert">{{ saveError }}</span>
            <button type="submit" class="btn-primary" [disabled]="settingsForm.invalid || saving">
              {{ saving ? 'Saving...' : 'Save settings' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  `,
  styles: [`
    .admin-container {
      max-width: 1080px;
      margin: 0 auto;
      padding: 28px 24px 48px;
    }

    .page-header {
      align-items: flex-start;
      display: flex;
      justify-content: space-between;
      gap: 24px;
      margin-bottom: 24px;
    }

    .eyebrow {
      color: #64748b;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      margin: 0 0 6px;
      text-transform: uppercase;
    }

    h2, h3, p { margin-top: 0; }

    h2 {
      color: #172033;
      font-size: 2rem;
      margin-bottom: 8px;
    }

    .intro, .panel-heading p, small { color: #64748b; }
    .intro { margin-bottom: 0; }

    .status-pill, .provider-mark {
      border: 1px solid #cbd5e1;
      border-radius: 999px;
      color: #64748b;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      padding: 7px 11px;
      white-space: nowrap;
    }

    .status-pill.ready {
      background: #ecfdf5;
      border-color: #a7f3d0;
      color: #047857;
    }

    .settings-panel {
      background: #ffffff;
      border: 1px solid #dbe3ec;
      border-radius: 8px;
      box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
      padding: 28px;
    }

    .panel-heading {
      align-items: flex-start;
      border-bottom: 1px solid #e7edf3;
      display: flex;
      justify-content: space-between;
      gap: 20px;
      margin-bottom: 24px;
      padding-bottom: 20px;
    }

    h3 { color: #172033; margin-bottom: 6px; }
    .panel-heading p { margin-bottom: 0; }
    .provider-mark { background: #f8fafc; color: #334155; }

    .form-grid {
      display: grid;
      gap: 22px;
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .form-field { display: flex; flex-direction: column; gap: 8px; }
    .form-field > span { color: #334155; font-size: 0.9rem; font-weight: 700; }

    input {
      border: 1px solid #cbd5e1;
      border-radius: 5px;
      box-sizing: border-box;
      color: #172033;
      font: inherit;
      min-height: 44px;
      padding: 10px 12px;
      width: 100%;
    }

    input:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
      outline: none;
    }

    small { font-size: 0.78rem; }
    .form-footer { align-items: center; display: flex; gap: 16px; justify-content: flex-end; margin-top: 28px; }

    button {
      border: 0;
      border-radius: 5px;
      cursor: pointer;
      font: inherit;
      font-weight: 700;
      min-height: 42px;
      padding: 10px 18px;
    }

    .btn-primary { background: #1d4ed8; color: white; }
    .btn-primary:disabled { background: #94a3b8; cursor: not-allowed; }
    .notice { color: #475569; font-size: 0.9rem; }
    .notice.success { color: #047857; }
    .notice.error { color: #b91c1c; }

    @media (max-width: 700px) {
      .admin-container { padding: 20px 16px 36px; }
      .page-header, .panel-heading, .form-footer { align-items: stretch; flex-direction: column; }
      .form-grid { grid-template-columns: 1fr; }
      .form-footer { gap: 12px; }
      .btn-primary { width: 100%; }
    }
  `]
})
export class AdminLlmsComponent implements OnInit {
  settings: OllamaSettings = { model: '', endpoint: '' };
  loading = true;
  saving = false;
  errorMessage = '';
  saveError = '';
  successMessage = '';

  constructor(private api: ApiService, private changeDetector: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.api.getOllamaSettings().subscribe({
      next: settings => {
        this.settings = settings;
        this.loading = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Unable to load Ollama settings.';
        this.changeDetector.markForCheck();
      }
    });
  }

  save(): void {
    this.saving = true;
    this.saveError = '';
    this.successMessage = '';
    this.api.updateOllamaSettings(this.settings).subscribe({
      next: settings => {
        this.settings = settings;
        this.saving = false;
        this.successMessage = 'LLM settings saved.';
        this.changeDetector.markForCheck();
      },
      error: error => {
        this.saving = false;
        this.saveError = error?.error?.message || 'Unable to save Ollama settings.';
        this.changeDetector.markForCheck();
      }
    });
  }
}
