import type { Booking } from "./status";
import { fareCopy, ticketCopy } from "./status";

export function Ticket({ booking, kind = "seat" }: { booking: Booking; kind?: "seat" | "fare" }) {
  const copy = kind === "fare" ? fareCopy(booking.status) : ticketCopy(booking.status);
  return (
    <aside className={`ticket ${booking.status.toLowerCase()}`}>
      <p className="eyebrow">Your ticket</p>
      <h2>{copy.title}</h2>
      <p>{copy.body}</p>
    </aside>
  );
}
