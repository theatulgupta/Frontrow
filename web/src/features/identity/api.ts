import { request } from "../../shared/api/http";

export function issueToken(userId: string) {
  return request<{ token: string; expiresAt: string }>("/api/dev/tokens", {
    method: "POST",
    body: JSON.stringify({ userId }),
  });
}
