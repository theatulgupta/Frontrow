export type Show = {
  id: string;
  name: string;
  startsAt: string;
  priceCents: number;
  venueName: string;
};

export type Seat = {
  seatId: string;
  section: string;
  rowLabel: string;
  seatNumber: number;
  frontRow: boolean;
  status: "AVAILABLE" | "HELD" | "SOLD";
};

export type Booking = {
  bookingId: string;
  showId: string;
  seatId: string;
  userId: string;
  status: "PENDING_PAYMENT" | "CONFIRMED" | "CANCELLED" | "EXPIRED";
  holdExpiresAt: string;
  priceCents: number;
  paymentStatus?: string;
};

export type ApiError = {
  code?: string;
  message?: string;
};

const jsonHeaders = { "Content-Type": "application/json" };

async function readError(response: Response): Promise<ApiError> {
  try {
    return (await response.json()) as ApiError;
  } catch {
    return { message: response.statusText };
  }
}

export async function issueToken(userId: string): Promise<string> {
  const response = await fetch("/api/dev/tokens", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ userId }),
  });
  if (!response.ok) {
    const error = await readError(response);
    throw new Error(error.message ?? "Could not sign in");
  }
  const body = (await response.json()) as { token: string };
  return body.token;
}

export async function listShows(): Promise<Show[]> {
  const response = await fetch("/api/shows");
  if (!response.ok) throw new Error("Could not load shows");
  return response.json();
}

export async function listSeats(showId: string): Promise<Seat[]> {
  const response = await fetch(`/api/shows/${showId}/seats`);
  if (!response.ok) throw new Error("Could not load seats");
  return response.json();
}

export async function bookSeat(showId: string, seatId: string, token: string): Promise<Booking> {
  const response = await fetch(`/api/shows/${showId}/bookings`, {
    method: "POST",
    headers: {
      ...jsonHeaders,
      Authorization: `Bearer ${token}`,
      "Idempotency-Key": crypto.randomUUID(),
    },
    body: JSON.stringify({ seatId }),
  });
  const body = await response.json();
  if (response.status === 202 || response.status === 200) return body as Booking;
  const error = body as ApiError;
  throw Object.assign(new Error(error.message ?? "Booking failed"), {
    status: response.status,
    code: error.code,
  });
}

export async function getBooking(bookingId: string, token: string): Promise<Booking> {
  const response = await fetch(`/api/bookings/${bookingId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (response.status === 410) {
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
  if (!response.ok) {
    const error = await readError(response);
    throw new Error(error.message ?? "Could not load booking");
  }
  return response.json();
}
