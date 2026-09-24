import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { ApiError } from "../../shared/api/http";
import { formatMoney } from "../../shared/money";
import { sessionStore, useSession } from "../../shared/session/session";
import { listSeats, listShows } from "../catalog/api";
import { SeatMap } from "../catalog/SeatMap";
import type { Seat } from "../catalog/types";
import { bookSeat, getBooking } from "./api";
import { bookingFailureMessage, pollInterval } from "./status";
import { Ticket } from "./Ticket";

export function HousePage() {
  const navigate = useNavigate();
  const session = useSession();
  const client = useQueryClient();
  const [bookingId, setBookingId] = useState<string | null>(null);
  const [showId, setShowId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const token = session?.token ?? "";

  const shows = useQuery({ queryKey: ["shows"], queryFn: listShows });
  const show = shows.data?.find((item) => item.id === showId)
    ?? shows.data?.find((item) => item.name === "Opening Night")
    ?? shows.data?.[0];

  const seats = useQuery({
    queryKey: ["seats", show?.id],
    queryFn: () => listSeats(show!.id),
    enabled: Boolean(show),
    refetchInterval: 2000,
  });

  const booking = useQuery({
    queryKey: ["booking", bookingId],
    queryFn: () => getBooking(bookingId!, token),
    enabled: Boolean(bookingId && token),
    refetchInterval: (query) => pollInterval(query.state.data?.status),
  });

  const reserve = useMutation({
    mutationFn: (seat: Seat) => bookSeat(show!.id, seat.seatId, token),
    onSuccess: (created) => {
      setBookingId(created.bookingId);
      setNotice(null);
      void client.invalidateQueries({ queryKey: ["seats", show?.id] });
    },
    onError: (error) => {
      const failure = error instanceof ApiError ? error : new ApiError(0, "UNKNOWN", error.message);
      setNotice(bookingFailureMessage(failure.status, failure.message));
    },
  });

  const sold = seats.data?.filter((seat) => seat.status === "SOLD").length ?? 0;
  const held = seats.data?.filter((seat) => seat.status === "HELD").length ?? 0;
  const open = seats.data?.filter((seat) => seat.status === "AVAILABLE").length ?? 0;
  const frontRow = seats.data?.filter((seat) => seat.frontRow && seat.holderName) ?? [];

  return (
    <main className="house">
      <header>
        <div>
          <p className="eyebrow">{show?.venueName ?? "Frontrow Hall"}</p>
          <h1>{show?.name ?? "Opening Night"}</h1>
          <p className="meta">
            {show ? `${formatMoney(show.priceCents)} · ` : ""}signed in as {session?.userId}
          </p>
        </div>
        <button
          className="ghost"
          onClick={() => {
            sessionStore.signOut();
            setBookingId(null);
            void navigate({ to: "/" });
          }}
        >
          Sign out
        </button>
      </header>
      <div className="shows" role="tablist" aria-label="Shows">
        {shows.data?.map((item) => (
          <button
            key={item.id}
            className={item.id === show?.id ? "show active" : "show"}
            onClick={() => {
              setShowId(item.id);
              setNotice(null);
            }}
          >
            <strong>{item.name}</strong>
            <span>{formatMoney(item.priceCents)} · {new Date(item.startsAt).toLocaleDateString(undefined, { month: "short", day: "numeric" })}</span>
          </button>
        ))}
      </div>
      <div className="stats">
        <span>{sold} sold</span>
        <span>{held} on hold</span>
        <span>{open} open</span>
      </div>
      {frontRow.length > 0 && (
        <p className="front-names">Front row: {frontRow.map((seat) => seat.holderName).join(" · ")}</p>
      )}
      <div className="stage">Stage</div>
      {shows.isError && <p className="notice">The show list did not load.</p>}
      {seats.isLoading && <p>Setting the house…</p>}
      {seats.isError && <p className="notice">The seat map did not load.</p>}
      {seats.data && <SeatMap seats={seats.data} pending={reserve.isPending} onSelect={(seat) => reserve.mutate(seat)} />}
      {notice && <p className="notice">{notice}</p>}
      {booking.data && <Ticket booking={booking.data} />}
    </main>
  );
}
