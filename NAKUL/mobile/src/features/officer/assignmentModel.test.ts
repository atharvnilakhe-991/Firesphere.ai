import { describe, expect, it } from 'vitest';
import { assignmentMatchesStatus, normalizeAssignmentStatus } from './assignmentModel';

describe('assignment status mapping', () => {
  it('normalizes backend enum values for display filters', () => {
    expect(normalizeAssignmentStatus('IN_PROGRESS')).toBe('in progress');
    expect(normalizeAssignmentStatus('COMPLETED')).toBe('completed');
  });

  it('matches normalized status and keeps the all filter inclusive', () => {
    expect(assignmentMatchesStatus('IN_PROGRESS', 'In progress')).toBe(true);
    expect(assignmentMatchesStatus('ASSIGNED', 'Completed')).toBe(false);
    expect(assignmentMatchesStatus('COMPLETED', 'All')).toBe(true);
  });
});
