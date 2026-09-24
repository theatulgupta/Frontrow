import type { Seat } from "./types";
import { rowsOf } from "./rows";

type Props = {
  seats: Seat[];
  pending: boolean;
  onSelect: (seat: Seat) => void;
};

export function SeatMap({ seats, pending, onSelect }: Props) {
  return (
    <section className="floor" aria-label="Seat map">
      {rowsOf(seats).map(([row, rowSeats]) => (
        <div className={rowSeats[0]?.frontRow ? "row front" : "row"} key={row}>
          <span>{rowSeats[0]?.frontRow ? "Front row" : `Row ${row}`}</span>
          <div>
            {rowSeats.map((seat) => (
              <button
                key={seat.seatId}
                className={`seat ${seat.status.toLowerCase()} ${seat.frontRow ? "gold" : ""}`}
                disabled={seat.status !== "AVAILABLE" || pending}
                onClick={() => onSelect(seat)}
                title={seat.holderName ?? seat.status.toLowerCase()}
                aria-label={`${seat.frontRow ? "Front row" : `Row ${seat.rowLabel}`} seat ${seat.seatNumber}, ${seat.status.toLowerCase()}${seat.holderName ? `, ${seat.holderName}` : ""}`}
              >
                {seat.seatNumber}
              </button>
            ))}
          </div>
        </div>
      ))}
    </section>
  );
}
