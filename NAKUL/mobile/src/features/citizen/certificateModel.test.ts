import { describe, expect, test } from 'vitest';
import { filterCertificates, type CertificateRecord } from './certificateModel';

const records: CertificateRecord[] = [
  { id: 'active-expiring', status: 'ACTIVE', expiresSoon: true },
  { id: 'active-valid', status: 'ACTIVE', expiresSoon: false },
];

describe('citizen certificate filters', () => {
  test('archived excludes active records and returns an empty list when none exist', () => {
    expect(filterCertificates(records, 'Archived')).toEqual([]);
  });

  test('active and expiring filters retain only matching active records', () => {
    expect(filterCertificates(records, 'Active').map((record) => record.id)).toEqual(['active-expiring', 'active-valid']);
    expect(filterCertificates(records, 'Expiring').map((record) => record.id)).toEqual(['active-expiring']);
  });

  test('archived filter includes archived records when available', () => {
    expect(filterCertificates([...records, { id: 'archived', status: 'ARCHIVED', expiresSoon: false }], 'Archived').map((record) => record.id)).toEqual(['archived']);
  });
});
