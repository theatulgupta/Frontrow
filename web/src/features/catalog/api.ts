import { request } from "../../shared/api/http";
import type { Patron, Seat, Show } from "./types";

export function listShows() {
  return request<Show[]>("/api/shows");
}

export function listSeats(showId: string) {
  return request<Seat[]>(`/api/shows/${showId}/seats`);
}

export function listPatrons() {
  return request<Patron[]>("/api/patrons");
}
