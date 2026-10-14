export type CertificateStatus = 'ACTIVE' | 'ARCHIVED';
export type CertificateFilter = 'Active' | 'Expiring' | 'Archived';

export interface CertificateRecord {
  id: string;
  status: CertificateStatus;
  expiresSoon: boolean;
}

/** Apply the citizen vault filter to the certificate records available locally. */
export function filterCertificates<T extends CertificateRecord>(
  records: readonly T[],
  filter: CertificateFilter,
): T[] {
  if (filter === 'Archived') return records.filter((record) => record.status === 'ARCHIVED');
  if (filter === 'Expiring') return records.filter((record) => record.status === 'ACTIVE' && record.expiresSoon);
  return records.filter((record) => record.status === 'ACTIVE');
}
