import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { request } from "../../shared/api/http";
import { formatMoney } from "../../shared/money";

export type Trip = {
  id: string;
  title: string;
  code: string;
  originCode: string;
  destinationCode: string;
  departAt: string;
  arriveAt: string;
  detail: string;
  priceCents: number;
};

export type Flight = Trip & {
  airline: string;
  flightNumber: string;
  stops: number;
  cabin: string;
  capacity: number;
  seatsHeld: number;
  seatsSold: number;
};

export type Hotel = {
  id: string;
  name: string;
  city: string;
  stars: number;
  nightlyRateCents: number;
  totalCents: number;
  nights: number;
};

type Mode = "flights" | "trains" | "buses" | "hotels";

export function SearchPage({
  mode,
  from,
  to,
  date,
  city,
  checkIn,
  checkOut,
}: {
  mode: Mode;
  from: string;
  to: string;
  date: string;
  city: string;
  checkIn: string;
  checkOut: string;
}) {
  const results = useQuery({
    queryKey: ["travel", mode, from, to, date, city, checkIn, checkOut],
    queryFn: (): Promise<Hotel[] | Flight[] | Trip[]> => load(mode, { from, to, date, city, checkIn, checkOut }),
  });

  return (
    <main className="house">
      <p className="eyebrow">{mode}</p>
      <h1>{heading(mode, from, to, city)}</h1>
      <Link className="ghost link" to="/">Change search</Link>
      {results.isLoading && <p>Looking up {mode}…</p>}
      {results.isError && <p className="notice">That search did not complete.</p>}
      <div className="results">
        {mode === "hotels"
          ? (results.data as Hotel[] | undefined)?.map((hotel) => (
              <article className="result" key={hotel.id}>
                <strong>{hotel.name}</strong>
                <span>{hotel.city} · {hotel.stars} star · {hotel.nights} nights</span>
                <b>{formatMoney(hotel.totalCents)}</b>
              </article>
            ))
          : (results.data as Array<Flight | Trip> | undefined)?.map((trip) => (
              <article className="result" key={trip.id}>
                <strong>{"airline" in trip ? `${trip.airline} ${trip.flightNumber}` : trip.title}</strong>
                <span>
                  {trip.originCode} → {trip.destinationCode} · {new Date(trip.departAt).toLocaleString()} · {trip.detail ?? ("cabin" in trip ? trip.cabin : "")}
                </span>
                <b>{formatMoney(trip.priceCents)}</b>
                {mode === "flights" && <Link to="/flights/$offerId" params={{ offerId: trip.id }}>Review fare</Link>}
              </article>
            ))}
        {results.data && results.data.length === 0 && <p>No {mode} match that search.</p>}
      </div>
    </main>
  );
}

function heading(mode: Mode, from: string, to: string, city: string) {
  if (mode === "hotels") return `Stays in ${city}`;
  return `${from} to ${to}`;
}

function load(
  mode: Mode,
  query: { from: string; to: string; date: string; city: string; checkIn: string; checkOut: string },
): Promise<Hotel[] | Flight[] | Trip[]> {
  if (mode === "hotels") {
    return request<Hotel[]>(`/api/hotels?city=${encodeURIComponent(query.city)}&checkIn=${query.checkIn}&checkOut=${query.checkOut}`);
  }
  const path = `/api/${mode}?from=${encodeURIComponent(query.from)}&to=${encodeURIComponent(query.to)}&date=${query.date}`;
  if (mode === "flights") return request<Flight[]>(path);
  return request<Trip[]>(path);
}
