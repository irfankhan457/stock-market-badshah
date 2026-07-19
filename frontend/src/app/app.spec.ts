import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Stock Badshah buying guide');
  });

  it('should distinguish an unreachable gateway from a backend HTTP 500', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const formatError = (error: unknown) => {
      return (app as unknown as { toUserErrorMessage: (value: unknown) => string }).toUserErrorMessage(error);
    };

    expect(formatError({ status: 0, message: 'Unknown Error' }))
      .toContain('Could not connect to the API gateway at http://localhost:8080');

    const backendError = formatError({
      status: 500,
      message: 'Http failure response for http://localhost:8080/stocks: 500 Internal Server Error',
      error: { detail: 'NSE index list returned 503 Service Unavailable.' }
    });
    expect(backendError).toContain('The backend is running');
    expect(backendError).toContain('HTTP 500');
    expect(backendError).toContain('NSE index list returned 503 Service Unavailable.');
  });

  it('should explain gateway and upstream service failures', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const formatError = (error: unknown) => {
      return (app as unknown as { toUserErrorMessage: (value: unknown) => string }).toUserErrorMessage(error);
    };

    const message = formatError({
      status: 503,
      message: 'Http failure response',
      error: { error: 'Service Unavailable' }
    });
    expect(message).toContain('API gateway responded');
    expect(message).toContain('external market-data provider');
    expect(message).toContain('HTTP 503');
    expect(message).toContain('Service Unavailable');
  });

  it('should read structural nested error detail, message, and error fields', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const formatError = (error: unknown) => {
      return (app as unknown as { toUserErrorMessage: (value: unknown) => string }).toUserErrorMessage(error);
    };

    expect(formatError({ status: 400, error: { detail: 'Detail text' } })).toContain('Detail text');
    expect(formatError({ status: 400, error: { message: 'Message text' } })).toContain('Message text');
    expect(formatError({ status: 400, error: { error: 'Error text' } })).toContain('Error text');
  });

  it('should keep timeout errors separate from connectivity failures', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const message = (app as unknown as { toUserErrorMessage: (value: unknown) => string })
      .toUserErrorMessage({ name: 'TimeoutError', message: 'Timeout has occurred' });

    expect(message).toContain('request timed out');
    expect(message).toContain('may still be processing');
  });
});
