import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { useState } from "react";
import { listPatrons } from "../catalog/api";
import { sessionStore } from "../../shared/session/session";
import { issueToken } from "./api";

export function SignInPage() {
  const navigate = useNavigate();
  const { next } = useSearch({ from: "/sign-in" });
  const [name, setName] = useState("");
  const patrons = useQuery({ queryKey: ["patrons"], queryFn: listPatrons });
  const signIn = useMutation({
    mutationFn: (userId: string) => issueToken(userId),
    onSuccess: (issued, userId) => {
      sessionStore.signIn(userId, issued.token);
      void navigate({ href: next });
    },
  });

  return (
    <main className="gate">
      <p className="eyebrow">Frontrow</p>
      <h1>Sign in to hold a fare.</h1>
      <Link className="ghost link" to="/">Back to search</Link>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const userId = name.trim();
          if (userId.length >= 2) signIn.mutate(userId);
        }}
      >
        <label>
          Your name
          <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ada" autoFocus />
        </label>
        <button type="submit" disabled={signIn.isPending || name.trim().length < 2}>
          Take your place
        </button>
        {signIn.isError && <p className="notice">{signIn.error.message}</p>}
      </form>
      <div className="patrons">
        {patrons.data?.map((patron) => (
          <button key={patron.userId} className="ghost" type="button" onClick={() => signIn.mutate(patron.userId)}>
            {patron.displayName}
            <span>{patron.city}</span>
          </button>
        ))}
      </div>
    </main>
  );
}
