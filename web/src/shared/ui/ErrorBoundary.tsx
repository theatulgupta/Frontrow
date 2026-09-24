import { Component, type ReactNode } from "react";

type Props = { children: ReactNode };
type State = { message: string | null };

export class ErrorBoundary extends Component<Props, State> {
  state: State = { message: null };

  static getDerivedStateFromError(error: Error): State {
    return { message: error.message };
  }

  render() {
    if (this.state.message) {
      return (
        <main className="gate">
          <p className="eyebrow">Frontrow</p>
          <h1>The house lights flickered.</h1>
          <p className="notice">{this.state.message}</p>
          <button type="button" onClick={() => window.location.assign("/")}>
            Start again
          </button>
        </main>
      );
    }
    return this.props.children;
  }
}
