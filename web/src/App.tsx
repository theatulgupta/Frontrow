import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { bookSeat, getBooking, issueToken, listSeats, listShows, type Seat } from "./api";

const money = (cents: number) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(cents / 100);

export function App() {
  const [userId, setUserId] = useState(() => sessionStorage.getItem("frontrow.user") ?? "");
  const [token, setToken] = useState(() => sessionStorage.getItem("frontrow.token") ?? "");
  const [draftName, setDraftName] = useState("");
  const [bookingId, setBookingId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const client = useQueryClient();

  const shows = useQuery({ queryKey: ["shows"], queryFn: listShows, enabled: Boolean(token) });
  const selected = shows.data?.[0];
  const activeShowId = selected?.id;

  const seats = useQuery({
    queryKey: ["seats", activeShowId],
    queryFn: () => listSeats(activeShowId!),
    enabled: Boolean(activeShowId),
    refetchInterval: 2000,
  });

  const booking = useQuery({
    queryKey: ["booking", bookingId],
    queryFn: () => getBooking(bookingId!, token),
    enabled: Boolean(bookingId && token),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "PENDING_PAYMENT" ? 1000 : false;
    },
  });

  const signIn = useMutation({
    mutationFn: issueToken,
    onSuccess: (nextToken, name) => {
      sessionStorage.setItem("frontrow.user", name);
      sessionStorage.setItem("frontrow.token", nextToken);
      setUserId(name);
      setToken(nextToken);
    },
  });

  const reserve = useMutation({
    mutationFn: (seat: Seat) => bookSeat(activeShowId!, seat.seatId, token),
    onSuccess: (created) => {
      setBookingId(created.bookingId);
      setNotice(null);
      void client.invalidateQueries({ queryKey: ["seats", activeShowId] });
    },
    onError: (error: Error & { code?: string; status?: number }) => {
      if (error.status === 409) setNotice("That seat was just taken. Pick another.");
      else if (error.status === 403) setNotice(error.message);
      else setNotice(error.message);
    },
  });

  if (!token) {
    return (
      <main className="gate">
        <p className="eyebrow">Frontrow</p>
        <h1>The front row is one seat wide.</h1>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            const name = draftName.trim();
            if (name.length >= 2) signIn.mutate(name);
          }}
        >
          <label>
            Your name
            <input value={draftName} onChange={(event) => setDraftName(event.target.value)} placeholder="Ada" autoFocus />
          </label>
          <button type="submit" disabled={signIn.isPending || draftName.trim().length < 2}>
            Take your place
          </button>
          {signIn.isError && <p className="notice">{(signIn.error as Error).message}</p>}
        </form>
      </main>
    );
  }

  const rows = groupRows(seats.data ?? []);

  return (
    <main className="house">
      <header>
        <div>
          <p className="eyebrow">Frontrow Hall</p>
          <h1>{selected?.name ?? "Opening Night"}</h1>
          <p className="meta">
            {selected ? money(selected.priceCents) : ""} · signed in as {userId}
          </p>
        </div>
        <button
          className="ghost"
          onClick={() => {
            sessionStorage.clear();
            setToken("");
            setUserId("");
            setBookingId(null);
          }}
        >
          Sign out
        </button>
      </header>

      <div className="stage">Stage</div>

      <section className="floor" aria-label="Seat map">
        {seats.isLoading && <p>Setting the house…</p>}
        {rows.map(([row, rowSeats]) => (
          <div className={rowSeats[0]?.frontRow ? "row front" : "row"} key={row}>
            <span>{rowSeats[0]?.frontRow ? "Front row" : `Row ${row}`}</span>
            <div>
              {rowSeats.map((seat) => (
                <button
                  key={seat.seatId}
                  className={`seat ${seat.status.toLowerCase()} ${seat.frontRow ? "gold" : ""}`}
                  disabled={seat.status !== "AVAILABLE" || reserve.isPending}
                  onClick={() => reserve.mutate(seat)}
                >
                  {seat.seatNumber}
                </button>
              ))}
            </div>
          </div>
        ))}
      </section>

      {notice && <p className="notice">{notice}</p>}

      {booking.data && (
        <aside className={`ticket ${booking.data.status.toLowerCase()}`}>
          <p className="eyebrow">Your ticket</p>
          <h2>{labelFor(booking.data.status)}</h2>
          <p>
            {booking.data.status === "PENDING_PAYMENT" && "Payment is running. This seat is held for you."}
            {booking.data.status === "CONFIRMED" && "Paid. The seat is yours."}
            {booking.data.status === "CANCELLED" && "The payment did not go through. The seat is free again."}
            {booking.data.status === "EXPIRED" && "The hold expired before payment finished."}
          </p>
        </aside>
      )}
    </main>
  );
}

function groupRows(seats: Seat[]) {
  const rows = new Map<string, Seat[]>();
  for (const seat of seats) {
    const list = rows.get(seat.rowLabel) ?? [];
    list.push(seat);
    rows.set(seat.rowLabel, list);
  }
  return [...rows.entries()];
}

function labelFor(status: string) {
  if (status === "PENDING_PAYMENT") return "Hold placed";
  if (status === "CONFIRMED") return "You’re in the front row";
  if (status === "CANCELLED") return "Payment declined";
  return "Hold expired";
}
