import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { ApiError, request } from "../../shared/api/http";
import { formatMoney } from "../../shared/money";
import { useSession } from "../../shared/session/session";
import { pollInterval } from "../booking/status";
import { Ticket } from "../booking/Ticket";
import { bookFlight, getFlightBooking } from "./api";
import type { Flight } from "./SearchPage";

export function FlightPage({ offerId }: { offerId: string }) {
  const session = useSession();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [bookingId, setBookingId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const offer = useQuery({
    queryKey: ["flight", offerId],
    queryFn: () => request<Flight>(`/api/flights/${offerId}`),
  });
  const booking = useQuery({
    queryKey: ["flight-booking", bookingId],
    queryFn: () => getFlightBooking(bookingId!, session!.token),
    enabled: Boolean(bookingId && session),
    refetchInterval: (query) => pollInterval(query.state.data?.status),
  });
  useEffect(() => {
    if (booking.data && booking.data.status !== "PENDING_PAYMENT" && booking.data.status !== "CONFIRMED") {
      void client.invalidateQueries({ queryKey: ["flight", offerId] });
    }
  }, [booking.data, client, offerId]);

  const reserve = useMutation({
    mutationFn: () => bookFlight(offerId, session!.token),
    onSuccess: (created) => {
      setBookingId(created.bookingId);
      setNotice(null);
      void client.invalidateQueries({ queryKey: ["flight", offerId] });
    },
    onError: (error) => {
      const failure = error instanceof ApiError ? error : new ApiError(0, "UNKNOWN", error.message);
      setNotice(failure.status === 409 ? "That fare is full." : failure.message);
    },
  });

  const flight = offer.data;
  const seatsLeft = flight ? flight.capacity - flight.seatsHeld - flight.seatsSold : 0;
  const departDate = flight?.departAt.slice(0, 10) ?? "";

  return (
    <main className="house">
      <Link
        className="ghost link"
        to="/flights"
        search={{ from: flight?.originCode ?? "DEL", to: flight?.destinationCode ?? "BOM", date: departDate }}
      >
        Back to flights
      </Link>
      {offer.isLoading && <p>Loading fare…</p>}
      {flight && (
        <>
          <p className="eyebrow">{flight.airline} {flight.flightNumber}</p>
          <h1>{flight.originCode} to {flight.destinationCode}</h1>
          <p className="meta">
            {new Date(flight.departAt).toLocaleString()} · {flight.stops === 0 ? "Nonstop" : `${flight.stops} stop`} · {flight.cabin} · {formatMoney(flight.priceCents)} · {seatsLeft} left
          </p>
          <button
            type="button"
            disabled={reserve.isPending || seatsLeft < 1}
            onClick={() => {
              if (!session) {
                void navigate({ to: "/sign-in", search: { next: `/flights/${offerId}` } });
                return;
              }
              reserve.mutate();
            }}
          >
            Hold this fare
          </button>
        </>
      )}
      {notice && <p className="notice">{notice}</p>}
      {booking.data && <Ticket booking={booking.data} kind="fare" />}
    </main>
  );
}
