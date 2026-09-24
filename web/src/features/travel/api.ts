import { ApiError, request } from "../../shared/api/http";
import type { Booking } from "../booking/status";

type FlightHold = {
  bookingId: string;
  offerId: string;
  userId: string;
  status: Booking["status"];
  holdExpiresAt: string;
  priceCents: number;
};

export function bookFlight(offerId: string, token: string) {
  return request<FlightHold>(
    `/api/flights/${offerId}/bookings`,
    {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
    },
    token,
  );
}

export async function getFlightBooking(bookingId: string, token: string): Promise<Booking> {
  try {
    const hold = await request<FlightHold>(`/api/flight-bookings/${bookingId}`, {}, token);
    return {
      bookingId: hold.bookingId,
      showId: hold.offerId,
      seatId: hold.offerId,
      userId: hold.userId,
      status: hold.status,
      holdExpiresAt: hold.holdExpiresAt,
      priceCents: hold.priceCents,
    };
  } catch (error) {
    if (error instanceof ApiError && error.status === 410) {
      return {
        bookingId,
        showId: "",
        seatId: "",
        userId: "",
        status: "EXPIRED",
        holdExpiresAt: "",
        priceCents: 0,
      };
    }
    throw error;
  }
}
