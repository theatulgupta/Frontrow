export type Show = {
  id: string;
  name: string;
  startsAt: string;
  priceCents: number;
  venueName: string;
};

export type SeatStatus = "AVAILABLE" | "HELD" | "SOLD";

export type Seat = {
  seatId: string;
  section: string;
  rowLabel: string;
  seatNumber: number;
  frontRow: boolean;
  status: SeatStatus;
  holderName?: string | null;
};

export type Patron = {
  userId: string;
  displayName: string;
  city: string;
};
