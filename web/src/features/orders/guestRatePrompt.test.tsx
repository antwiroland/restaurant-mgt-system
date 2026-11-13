import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GuestRatePrompt } from "@/components/GuestRatePrompt";
import { forgetOrder, rememberOrder, rememberedOrders } from "@/features/orders/guestOrders";

const HOUR = 60 * 60 * 1000;

describe("orders remembered on the guest's phone", () => {
  beforeEach(() => window.localStorage.clear());

  it("keeps only this table's orders, drops old ones and forgets on request", () => {
    const now = Date.now();
    rememberOrder("table-a", "o1", now - 9 * HOUR); // yesterday-ish: too old
    rememberOrder("table-a", "o2", now - HOUR);
    rememberOrder("table-b", "o3", now);
    expect(rememberedOrders("table-a", now).map((o) => o.orderId)).toEqual(["o2"]);
    expect(rememberedOrders("table-b", now).map((o) => o.orderId)).toEqual(["o3"]);
    forgetOrder("o2");
    expect(rememberedOrders("table-a", now)).toEqual([]);
  });
});

describe("How was your meal? prompt", () => {
  beforeEach(() => window.localStorage.clear());
  afterEach(() => vi.unstubAllGlobals());

  function stubServer(answers: Record<string, { status: number; body?: unknown }>) {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const match = Object.keys(answers).find((id) => url.includes(id));
      if (init?.method === "POST") return new Response(JSON.stringify({ id: "fb" }), { status: 201 });
      const answer = match ? answers[match] : { status: 404 };
      return new Response(answer.body ? JSON.stringify(answer.body) : "", { status: answer.status });
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  it("offers a served order this phone placed, and forgets it once rated", async () => {
    rememberOrder("tok", "served-order");
    const fetchMock = stubServer({
      "served-order": { status: 200, body: { orderId: "served-order", orderNumber: "SERVEDOR", status: "COMPLETED", rateable: true, rated: false } },
    });
    render(<GuestRatePrompt tableToken="tok" shownOrderIds={[]} />);

    expect(await screen.findByText("How was your meal?")).toBeInTheDocument();
    expect(screen.getByText("#SERVEDOR")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("radio", { name: "5 stars" }));
    await userEvent.click(screen.getByRole("button", { name: "Send rating" }));

    expect(await screen.findByText("Thanks for rating this order!")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/orders/served-order/feedback"), expect.objectContaining({ method: "POST" }));
    expect(rememberedOrders("tok")).toEqual([]);
  });

  it("shows nothing for expired orders (and forgets them), still-cooking ones, or orders already on the table page", async () => {
    rememberOrder("tok", "expired-order");
    rememberOrder("tok", "cooking-order");
    rememberOrder("tok", "on-page-order");
    stubServer({
      "expired-order": { status: 410 },
      "cooking-order": { status: 200, body: { orderId: "cooking-order", orderNumber: "COOKINGO", status: "PREPARING", rateable: false, rated: false } },
    });
    const { container } = render(<GuestRatePrompt tableToken="tok" shownOrderIds={["on-page-order"]} />);

    await waitFor(() => expect(rememberedOrders("tok").map((o) => o.orderId)).toEqual(["cooking-order", "on-page-order"]));
    expect(container).toBeEmptyDOMElement();
  });

  it("a guest can dismiss the prompt", async () => {
    rememberOrder("tok", "served-order");
    stubServer({
      "served-order": { status: 200, body: { orderId: "served-order", orderNumber: "SERVEDOR", status: "COMPLETED", rateable: true, rated: false } },
    });
    render(<GuestRatePrompt tableToken="tok" shownOrderIds={[]} />);
    await userEvent.click(await screen.findByRole("button", { name: "Not now" }));
    expect(screen.queryByText("How was your meal?")).not.toBeInTheDocument();
    expect(rememberedOrders("tok")).toEqual([]);
  });
});
