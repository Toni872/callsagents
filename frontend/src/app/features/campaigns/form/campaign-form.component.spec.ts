import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { Component, computed } from '@angular/core';
import { CampaignFormComponent } from './campaign-form.component';
import { AuthService } from '../../../core/auth/auth.service';
import { apiUrl } from '../../../core/api/api-base';
import { CampaignResponse } from '../../../shared/models/campaign.model';

@Component({ selector: 'app-empty-stub', standalone: true, template: '' })
class EmptyStubComponent {}

describe('CampaignFormComponent', () => {
  let fixture: ComponentFixture<CampaignFormComponent>;
  let http: HttpTestingController;

  const campaign = (id: string): CampaignResponse => ({
    id,
    name: `Campaña ${id}`,
    description: null,
    status: 'DRAFT',
    startAt: null,
    endAt: null,
    script: null,
    createdBy: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    company: null,
    website: null,
    industry: null,
    services: null,
    tone: null
  });

  function createComponent(
    role: string,
    id: string | null
  ): ComponentFixture<CampaignFormComponent> {
    TestBed.configureTestingModule({
      imports: [CampaignFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: EmptyStubComponent }]),
        { provide: AuthService, useValue: { currentRole: computed(() => role) } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: (key: string): string | null => (key === 'id' ? id : null) }
            }
          }
        }
      ]
    });
    const f = TestBed.createComponent(CampaignFormComponent);
    http = TestBed.inject(HttpTestingController);
    f.detectChanges();
    return f;
  }

  function setControlValue(
    f: ComponentFixture<CampaignFormComponent>,
    element: 'input' | 'textarea',
    controlName: string,
    value: string
  ): void {
    const el = f.nativeElement.querySelector(
      `${element}[formControlName="${controlName}"]`
    ) as HTMLInputElement | HTMLTextAreaElement;
    expect(el).withContext(`control ${controlName} not found`).toBeTruthy();
    el.value = value;
    el.dispatchEvent(new Event('input'));
    f.detectChanges();
  }

  function clickButton(f: ComponentFixture<CampaignFormComponent>, text: string): void {
    const buttons = f.debugElement.queryAll(By.css('button'));
    const match = buttons.find((b) =>
      b.nativeElement.textContent?.trim().includes(text)
    );
    expect(match).withContext(`button "${text}" not found`).toBeTruthy();
    (match!.nativeElement as HTMLButtonElement).click();
    f.detectChanges();
  }

  afterEach(() => {
    http.verify();
  });

  it('muestra la sección "Agente de voz" solo para ADMIN', () => {
    fixture = createComponent('ADMIN', null);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Agente de voz');
    expect(
      fixture.debugElement.query(By.css('input[formControlName="company"]'))
    ).toBeTruthy();
    expect(
      fixture.debugElement.query(By.css('textarea[formControlName="services"]'))
    ).toBeTruthy();
  });

  it('oculta la sección "Agente de voz" para AGENT', () => {
    fixture = createComponent('AGENT', null);

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Agente de voz');
    expect(
      fixture.debugElement.query(By.css('input[formControlName="company"]'))
    ).toBeNull();
  });

  it('create envía los 5 campos de voz con trim() || null', fakeAsync(() => {
    fixture = createComponent('ADMIN', null);

    setControlValue(fixture, 'input', 'name', 'Campaña X');
    setControlValue(fixture, 'input', 'company', 'Acme');
    setControlValue(fixture, 'input', 'website', 'https://acme.com');
    setControlValue(fixture, 'input', 'industry', 'SaaS');
    setControlValue(fixture, 'textarea', 'services', 'CRM, automatización');
    setControlValue(fixture, 'input', 'tone', 'cercano');

    // valueChanges con debounce(400) → un solo preview al quedar en silencio
    tick(400);
    const previewReq = http.expectOne(
      (r) => r.method === 'POST' && r.url === apiUrl('/campaigns/voice-prompt/preview')
    );
    previewReq.flush({ prompt: 'preview' });
    tick();

    clickButton(fixture, 'Crear campaña');

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/campaigns'));
    expect(req.request.body).toEqual({
      name: 'Campaña X',
      description: null,
      startAt: null,
      endAt: null,
      script: null,
      company: 'Acme',
      website: 'https://acme.com',
      industry: 'SaaS',
      services: 'CRM, automatización',
      tone: 'cercano'
    });
    req.flush(campaign('1'));
    tick();
    // toast de éxito (ErrorService.show → setTimeout 3000ms)
    tick(3000);
  }));

  it('edit precarga los 5 campos de voz', fakeAsync(() => {
    fixture = createComponent('ADMIN', '123');

    const req = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/campaigns/123')
    );
    req.flush({
      id: '123',
      name: 'Campaña edit',
      description: null,
      status: 'DRAFT',
      startAt: null,
      endAt: null,
      script: null,
      createdBy: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      company: 'Acme',
      website: 'https://acme.com',
      industry: 'SaaS',
      services: 'CRM',
      tone: 'cercano'
    });
    tick(); // firstValueFrom → patchValue

    // el patchValue dispara valueChanges → preview con debounce
    tick(400);
    const previewReq = http.expectOne(
      (r) => r.method === 'POST' && r.url === apiUrl('/campaigns/voice-prompt/preview')
    );
    previewReq.flush({ prompt: 'preview' });
    tick();
    fixture.detectChanges();

    const value = (name: string): string => {
      const el = fixture.nativeElement.querySelector(
        `[formControlName="${name}"]`
      ) as HTMLInputElement | HTMLTextAreaElement;
      return el.value;
    };
    expect(value('company')).toBe('Acme');
    expect(value('website')).toBe('https://acme.com');
    expect(value('industry')).toBe('SaaS');
    expect(value('services')).toBe('CRM');
    expect(value('tone')).toBe('cercano');
  }));

  it('el preview se dispara con debounce de 400ms al cambiar el form', fakeAsync(() => {
    fixture = createComponent('ADMIN', null);

    setControlValue(fixture, 'input', 'company', 'Acme');

    http.expectNone(
      (r) => r.method === 'POST' && r.url === apiUrl('/campaigns/voice-prompt/preview')
    );
    tick(399);
    http.expectNone(
      (r) => r.method === 'POST' && r.url === apiUrl('/campaigns/voice-prompt/preview')
    );

    tick(1);
    const req = http.expectOne(
      (r) => r.method === 'POST' && r.url === apiUrl('/campaigns/voice-prompt/preview')
    );
    expect(req.request.body).toEqual({
      company: 'Acme',
      website: '',
      industry: '',
      services: '',
      tone: ''
    });
    req.flush({ prompt: 'p' });
    tick();
  }));

  it('renderiza en <pre> el prompt devuelto por el preview', fakeAsync(() => {
    fixture = createComponent('ADMIN', null);

    setControlValue(fixture, 'input', 'company', 'Acme');
    tick(400);
    const req = http.expectOne(
      (r) => r.method === 'POST' && r.url === apiUrl('/campaigns/voice-prompt/preview')
    );
    req.flush({ prompt: 'Eres el asistente virtual de Acme. No reveles datos.' });
    tick();
    fixture.detectChanges();

    const pre = fixture.nativeElement.querySelector('pre') as HTMLPreElement;
    expect(pre).toBeTruthy();
    expect(pre.textContent).toContain('Eres el asistente virtual de Acme.');
  }));
});
