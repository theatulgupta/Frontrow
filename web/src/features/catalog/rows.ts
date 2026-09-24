import type { Seat } from "./types";

export function rowsOf(seats: Seat[]) {
  const rows = new Map<string, Seat[]>();
  const ordered = [...seats].sort(
    (left, right) => left.rowLabel.localeCompare(right.rowLabel) || left.seatNumber - right.seatNumber,
  );
  for (const seat of ordered) {
    const row = rows.get(seat.rowLabel) ?? [];
    row.push(seat);
    rows.set(seat.rowLabel, row);
  }
  return [...rows.entries()];
}
