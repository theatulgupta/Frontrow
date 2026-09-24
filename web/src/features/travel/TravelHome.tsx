import { Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { useSession } from "../../shared/session/session";

const tomorrow = () => {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  return date.toISOString().slice(0, 10);
};

export function TravelHome() {
  const session = useSession();
  const navigate = useNavigate();
  const [from, setFrom] = useState("DEL");
  const [to, setTo] = useState("BOM");
  const [date, setDate] = useState(tomorrow);
  const [city, setCity] = useState("Delhi");
  const [checkOut, setCheckOut] = useState(() => {
    const date = new Date();
    date.setDate(date.getDate() + 2);
    return date.toISOString().slice(0, 10);
  });

  return (
    <main className="house">
      <header>
        <div>
          <p className="eyebrow">Frontrow</p>
          <h1>Where to next?</h1>
          <p className="meta">{session ? `Signed in as ${session.userId}` : "Search freely. Sign in when you book a flight."}</p>
        </div>
        {session ? (
          <Link className="ghost link" to="/shows">Events</Link>
        ) : (
          <Link className="ghost link" to="/sign-in" search={{ next: "/" }}>Sign in</Link>
        )}
      </header>
      <form
        className="search"
        onSubmit={(event) => {
          event.preventDefault();
          void navigate({ to: "/flights", search: { from, to, date } });
        }}
      >
        <label>From<input value={from} onChange={(event) => setFrom(event.target.value.toUpperCase())} /></label>
        <label>To<input value={to} onChange={(event) => setTo(event.target.value.toUpperCase())} /></label>
        <label>Date<input type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>
        <button type="submit">Search flights</button>
      </form>
      <div className="modes">
        <Link to="/flights" search={{ from, to, date }}>Flights</Link>
        <Link to="/trains" search={{ from, to, date }}>Trains</Link>
        <Link to="/buses" search={{ from, to, date }}>Buses</Link>
        <Link to="/hotels" search={{ city, checkIn: date, checkOut }}>Hotels</Link>
        <Link to="/shows">Events</Link>
      </div>
      <form
        className="search"
        onSubmit={(event) => {
          event.preventDefault();
          void navigate({ to: "/hotels", search: { city, checkIn: date, checkOut } });
        }}
      >
        <label>City<input value={city} onChange={(event) => setCity(event.target.value)} /></label>
        <label>Check in<input type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>
        <label>Check out<input type="date" value={checkOut} onChange={(event) => setCheckOut(event.target.value)} /></label>
        <button type="submit">Search hotels</button>
      </form>
    </main>
  );
}
