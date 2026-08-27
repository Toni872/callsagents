import {
  ChangeDetectionStrategy,
  Component,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { Router } from '@angular/router';
import { BusinessApi } from '../../core/api/business.api';
import { BusinessInfoStepComponent } from './steps/business-info-step.component';
import { ChatbotConfigStepComponent } from './steps/chatbot-config-step.component';
import { WidgetPreviewStepComponent } from './steps/widget-preview-step.component';
import { BusinessProfileRequest } from '../../shared/models/business-profile.model';

@Component({
  selector: 'app-onboarding-wizard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BusinessInfoStepComponent,
    ChatbotConfigStepComponent,
    WidgetPreviewStepComponent
  ],
  template: `
    <div class="wizard">
      <div class="wizard__card">
        <div class="wizard__stepper">
          @for (step of steps; track step; let i = $index) {
            <div class="wizard__step" [class.wizard__step--active]="currentStep() === i" [class.wizard__step--done]="currentStep() > i">
              <span class="wizard__step-num">{{ i + 1 }}</span>
              <span class="wizard__step-label">{{ step }}</span>
            </div>
            @if (i < steps.length - 1) {
              <div class="wizard__line" [class.wizard__line--done]="currentStep() > i"></div>
            }
          }
        </div>

        @if (currentStep() === 0) {
          <app-business-info-step
            #step0
            (next)="onNextStep()"
          ></app-business-info-step>
        } @else if (currentStep() === 1) {
          <app-chatbot-config-step
            #step1
            (next)="onNextStep()"
            (prev)="onPrevStep()"
          ></app-chatbot-config-step>
        } @else if (currentStep() === 2) {
          <app-widget-preview-step
            [companyName]="collectedData()['companyName']"
            [botName]="collectedData()['botName']"
            [tone]="collectedData()['tone']"
            [greeting]="collectedData()['greeting']"
            [chatColor]="collectedData()['chatColor']"
            [saving]="saving()"
            (prev)="onPrevStep()"
            (complete)="onComplete()"
            (skip)="onSkip()"
          ></app-widget-preview-step>
        }
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: flex; align-items: center; justify-content: center;
      min-height: 100vh; background: var(--color-bg);
    }
    .wizard {
      width: 100%; display: flex; justify-content: center; align-items: center;
      padding: var(--spacing-6) var(--spacing-4);
    }
    .wizard__card {
      width: 100%; max-width: 520px; background: var(--color-surface);
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      padding: var(--spacing-8) var(--spacing-8) var(--spacing-6);
      box-shadow: var(--shadow-sm);
    }
    .wizard__stepper {
      display: flex; align-items: center; justify-content: center;
      gap: 0; margin-bottom: var(--spacing-8);
    }
    .wizard__step {
      display: flex; align-items: center; gap: var(--spacing-2);
    }
    .wizard__step-num {
      width: 28px; height: 28px; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      font-size: 0.75rem; font-weight: 600;
      background: var(--color-bg-alt); color: var(--color-text-muted);
      border: 1px solid var(--color-border);
      transition: all 0.2s;
    }
    .wizard__step--active .wizard__step-num {
      background: var(--color-primary); color: var(--color-on-primary);
      border-color: var(--color-primary);
    }
    .wizard__step--done .wizard__step-num {
      background: var(--color-success, #10b981); color: #fff;
      border-color: var(--color-success, #10b981);
    }
    .wizard__step-label {
      font-size: 0.75rem; color: var(--color-text-muted); font-weight: 500;
    }
    .wizard__step--active .wizard__step-label { color: var(--color-text-strong); font-weight: 600; }
    .wizard__line {
      flex: 1; height: 2px; margin: 0 var(--spacing-3);
      background: var(--color-border); transition: background 0.2s;
    }
    .wizard__line--done { background: var(--color-success, #10b981); }
  `]
})
export class OnboardingWizardComponent {
  private readonly businessApi = inject(BusinessApi);
  private readonly router = inject(Router);

  @ViewChild('step0') step0?: BusinessInfoStepComponent;
  @ViewChild('step1') step1?: ChatbotConfigStepComponent;

  readonly steps = ['Negocio', 'Chatbot', 'Vista previa'];
  readonly currentStep = signal(0);
  readonly saving = signal(false);
  readonly collectedData = signal<Record<string, string>>({});

  onNextStep(): void {
    if (this.currentStep() === 0 && this.step0) {
      if (!this.step0.validate()) return;
      this.collectedData.update(d => ({ ...d, ...this.step0!.getData() }));
    }
    if (this.currentStep() === 1 && this.step1) {
      if (!this.step1.validate()) return;
      this.collectedData.update(d => ({ ...d, ...this.step1!.getData() }));
    }
    if (this.currentStep() < 2) {
      this.currentStep.update(s => s + 1);
    }
  }

  onPrevStep(): void {
    if (this.currentStep() > 0) {
      this.currentStep.update(s => s - 1);
    }
  }

  onComplete(): void {
    this.saving.set(true);
    const data = this.collectedData();
    const request: BusinessProfileRequest = {
      companyName: data['companyName'] || '',
      website: data['website'] || undefined,
      industry: data['industry'] || undefined,
      services: data['services'] || undefined,
      tone: data['tone'] || 'profesional',
      botName: data['botName'] || 'Naiara',
      greeting: data['greeting'] || undefined,
      chatColor: data['chatColor'] || '#25D366'
    };

    this.businessApi.updateProfile(request).subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigateByUrl('/dashboard');
      },
      error: () => {
        this.saving.set(false);
      }
    });
  }

  onSkip(): void {
    this.saving.set(true);
    this.businessApi.createProfile().subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigateByUrl('/dashboard');
      },
      error: () => {
        this.saving.set(false);
      }
    });
  }
}
