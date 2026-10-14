export function normalizeAssignmentStatus(status: unknown): string {
  return String(status ?? 'Assigned').replaceAll('_', ' ').trim().toLowerCase();
}

export function assignmentMatchesStatus(status: unknown, filter: string): boolean {
  return filter === 'All' || normalizeAssignmentStatus(status) === normalizeAssignmentStatus(filter);
}
