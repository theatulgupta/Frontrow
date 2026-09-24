export type BookingStatus = "PENDING_PAYMENT" | "CONFIRMED" | "CANCELLED" | "EXPIRED";

export type Booking = {
  bookingId: string;
  showId: string;
  seatId: string;
  userId: string;
  status: BookingStatus;
  holdExpiresAt: string;
  priceCents: number;
  paymentStatus?: string;
};

export function isTerminal(status: BookingStatus | undefined) {
  return status === "CONFIRMED" || status === "CANCELLED" || status === "EXPIRED";
}

export function pollInterval(status: BookingStatus | undefined) {
  return status === "PENDING_PAYMENT" ? 1000 : false;
}

export function fareCopy(status: BookingStatus) {
  switch (status) {
    case "PENDING_PAYMENT":
      return { title: "Fare held", body: "Payment is running. This fare is held for you." };
    case "CONFIRMED":
      return { title: "You're booked", body: "Paid. The fare is yours." };
    case "CANCELLED":
      return { title: "Payment declined", body: "The payment did not go through. The fare is free again." };
    case "EXPIRED":
      return { title: "Hold expired", body: "The hold ended before payment finished." };
  }
}

export function ticketCopy(status: BookingStatus) {
  switch (status) {
    case "PENDING_PAYMENT":
      return { title: "Hold placed", body: "Payment is running. This seat is held for you." };
    case "CONFIRMED":
      return { title: "You’re in the front row", body: "Paid. The seat is yours." };
    case "CANCELLED":
      return { title: "Payment declined", body: "The payment did not go through. The seat is free again." };
    case "EXPIRED":
      return { title: "Hold expired", body: "The hold ended before payment finished." };
  }
}

export function bookingFailureMessage(status: number, message: string) {
  if (status === 409) return "That seat was just taken. Pick another.";
  if (status === 403) return message;
  if (status === 503) return "The seat lock is busy. Try again.";
  return message;
}
