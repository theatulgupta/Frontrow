import { ApiError, request } from "../../shared/api/http";
import type { Booking } from "./status";

export function bookSeat(showId: string, seatId: string, token: string) {
  return request<Booking>(
    `/api/shows/${showId}/bookings`,
    {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify({ seatId }),
    },
    token,
  );
}

export async function getBooking(bookingId: string, token: string): Promise<Booking> {
  try {
    return await request<Booking>(`/api/bookings/${bookingId}`, {}, token);
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
