import { describe, expect, it } from "vitest";
import { rowsOf } from "./features/catalog/rows";
import type { Seat } from "./features/catalog/types";
import { bookingFailureMessage, isTerminal, pollInterval, ticketCopy } from "./features/booking/status";

const seat = (row: string, number: number, frontRow: boolean): Seat => ({
  seatId: `${row}${number}`,
  section: "ORCHESTRA",
  rowLabel: row,
  seatNumber: number,
  frontRow,
  status: "AVAILABLE",
});

describe("seat rows", () => {
  it("keeps the front row ahead of the row behind it", () => {
    const rows = rowsOf([seat("B", 1, false), seat("A", 2, true), seat("A", 1, true)]);
    expect(rows.map(([label]) => label)).toEqual(["A", "B"]);
    expect(rows[0][1].map((item) => item.seatNumber)).toEqual([1, 2]);
  });
});

describe("booking status", () => {
  it("polls only while payment is in flight", () => {
    expect(pollInterval("PENDING_PAYMENT")).toBe(1000);
    expect(pollInterval("CONFIRMED")).toBe(false);
    expect(isTerminal("EXPIRED")).toBe(true);
    expect(isTerminal("PENDING_PAYMENT")).toBe(false);
  });

  it("explains a lost seat without exposing the status code", () => {
    expect(bookingFailureMessage(409, "Seat is not available")).toBe("That seat was just taken. Pick another.");
    expect(ticketCopy("CONFIRMED").title).toBe("You’re in the front row");
  });
});
