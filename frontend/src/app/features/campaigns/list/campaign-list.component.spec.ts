import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { CampaignListComponent } from './campaign-list.component';
import { apiUrl } from '../../../core/api/api-base';
import { PageResponse } from '../../../core/api/http.types';
import { CampaignResponse } from '../../../shared/models/campaign.model';

describe('CampaignListComponent', () => {
  let fixture: ComponentFixture<CampaignListComponent>;
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

  const page = (
    content: CampaignResponse[],
    totalPages = 1
  ): PageResponse<CampaignResponse> => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages,
    first: true,
    last: totalPages <= 1
  });

  function flushCampaigns(content: CampaignResponse[], totalPages = 1): void {
    const req = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/campaigns')
    );
    req.flush(page(content, totalPages));
    fixture.detectChanges();
  }

  function buttonByText(text: string): HTMLButtonElement {
    const buttons = fixture.debugElement.queryAll(By.css('button'));
    const match = buttons.find((b) =>
      b.nativeElement.textContent?.trim().includes(text)
    );
    expect(match).withContext(`button "${text}" not found`).toBeTruthy();
    return match!.nativeElement as HTMLButtonElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampaignListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CampaignListComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  it('loads and renders campaigns on init', () => {
    flushCampaigns([campaign('1'), campaign('2')]);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Campaña 1');
    expect(text).toContain('Campaña 2');
    expect(text).toContain('2 campañas');
  });

  it('pagination updates the page and refetches', () => {
    flushCampaigns([campaign('1')], 3);

    buttonByText('Siguiente').click();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/campaigns') &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '20'
    );
    req.flush(page([campaign('2')], 3));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Campaña 2');
  });
});
