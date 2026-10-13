import { describe, expect, test } from 'vitest';
import {
  createDemoAdmin,
  loadDemoRecords,
  loadDemoAiAnalyses,
  loadDemoEvidence,
  updateDemoFindingReview,
} from './mockServices';

describe('admin development adapter', () => {
  test('uses stable, explicitly identified local fixtures', () => {
    const first = loadDemoRecords('admin.applications');
    expect(first).toHaveLength(2);
    expect(first[0].id).toBe('demo-noc-201');
    expect(loadDemoRecords('admin.applications')).toEqual(first);
    expect(createDemoAdmin().token).toBe('local-demo:ADMIN');
  });

  test('keeps service-only pages visibly disconnected in development fixtures', () => {
    expect(loadDemoRecords('admin.vehicles')[0].status).toBe('Not connected');
    expect(loadDemoRecords('gis.layers')[0].source).toBe('No geographic data loaded');
  });

  test('provides Phase 7 AI advisory reports with gated model provenance', () => {
    const analyses = loadDemoAiAnalyses('demo-noc-201');
    expect(analyses).toHaveLength(1);
    const run = analyses[0];
    expect(run.cvStatus).toBe('NOT_APPROVED_GATED');
    expect(run.vlmStatus).toBe('UNAVAILABLE');
    expect(run.calibrationStatus).toBe('NOT_CALIBRATED');
    expect(run.modelApprovalStatus).toBe('NOT_APPROVED');
    expect(run.findings).toHaveLength(2);

    const f1 = run.findings[0];
    expect(f1.id).toBe('demo-finding-1');
    expect(f1.isAdvisory).toBe(true);
    expect(f1.reviewStatus).toBe('PENDING_REVIEW');
    expect(f1.boundingBoxJson).toContain('xMin');
  });

  test('provides Phase 7 inspection photographic evidence with integrity checksums', () => {
    const evidence = loadDemoEvidence('demo-insp-101');
    expect(evidence).toHaveLength(2);
    expect(evidence[0].fileName).toBe('corridor_wall_north.jpg');
    expect(evidence[0].sha256Hash).toHaveLength(64);
    expect(evidence[0].captureTimestamp).toBeDefined();
  });

  test('records officer finding verification decisions and updates finding review status', () => {
    const success = updateDemoFindingReview(
      'demo-noc-201',
      'demo-run-001',
      'demo-finding-1',
      'CONFIRMED',
      'Verified missing fire extinguisher cylinder in north corridor on-site.',
      true
    );
    expect(success).toBe(true);

    const updatedRun = loadDemoAiAnalyses('demo-noc-201')[0];
    const updatedFinding = updatedRun.findings.find((f) => f.id === 'demo-finding-1');
    expect(updatedFinding?.reviewStatus).toBe('CONFIRMED');
    expect(updatedFinding?.hazardVerified).toBe(true);
    expect(updatedFinding?.officerNotes).toContain('north corridor');
  });
});
