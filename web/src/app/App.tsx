import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createRootRoute, createRoute, createRouter, redirect, RouterProvider } from "@tanstack/react-router";
import { HousePage } from "../features/booking/HousePage";
import { SignInPage } from "../features/identity/SignInPage";
import { FlightPage } from "../features/travel/FlightPage";
import { SearchPage } from "../features/travel/SearchPage";
import { TravelHome } from "../features/travel/TravelHome";
import { sessionStore } from "../shared/session/session";
import { ErrorBoundary } from "../shared/ui/ErrorBoundary";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

function isoDay(offset: number) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  return date.toISOString().slice(0, 10);
}

function text(value: unknown, fallback: string) {
  return typeof value === "string" && value.length > 0 ? value : fallback;
}

const rootRoute = createRootRoute();

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: TravelHome,
});

const signInRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/sign-in",
  validateSearch: (search: Record<string, unknown>) => ({
    next: typeof search.next === "string" && search.next.startsWith("/") ? search.next : "/",
  }),
  component: SignInPage,
});

const tripSearch = (fallbackTo: string) => (search: Record<string, unknown>) => ({
  from: text(search.from, "DEL"),
  to: text(search.to, fallbackTo),
  date: text(search.date, isoDay(1)),
});

const flightsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/flights",
  validateSearch: tripSearch("BOM"),
  component: function Flights() {
    const { from, to, date } = flightsRoute.useSearch();
    return <SearchPage mode="flights" from={from} to={to} date={date} city="" checkIn="" checkOut="" />;
  },
});

const trainsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/trains",
  validateSearch: tripSearch("BOM"),
  component: function Trains() {
    const { from, to, date } = trainsRoute.useSearch();
    return <SearchPage mode="trains" from={from} to={to} date={date} city="" checkIn="" checkOut="" />;
  },
});

const busesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/buses",
  validateSearch: tripSearch("JAI"),
  component: function Buses() {
    const { from, to, date } = busesRoute.useSearch();
    return <SearchPage mode="buses" from={from} to={to} date={date} city="" checkIn="" checkOut="" />;
  },
});

const hotelsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/hotels",
  validateSearch: (search: Record<string, unknown>) => ({
    city: text(search.city, "Delhi"),
    checkIn: text(search.checkIn, isoDay(1)),
    checkOut: text(search.checkOut, isoDay(2)),
  }),
  component: function Hotels() {
    const { city, checkIn, checkOut } = hotelsRoute.useSearch();
    return <SearchPage mode="hotels" from="" to="" date="" city={city} checkIn={checkIn} checkOut={checkOut} />;
  },
});

const flightOfferRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/flights/$offerId",
  component: function Offer() {
    const { offerId } = flightOfferRoute.useParams();
    return <FlightPage offerId={offerId} />;
  },
});

const houseRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/shows",
  beforeLoad: () => {
    if (!sessionStore.get()) throw redirect({ to: "/sign-in", search: { next: "/shows" } });
  },
  component: HousePage,
});

const routeTree = rootRoute.addChildren([
  homeRoute,
  signInRoute,
  flightsRoute,
  trainsRoute,
  busesRoute,
  hotelsRoute,
  flightOfferRoute,
  houseRoute,
]);

const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

export function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
