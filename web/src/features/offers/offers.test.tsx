import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import type { PublicOfferRecord } from "@/lib/apiClient";
import { DishOffers, OfferRibbon, OffersStrip, offersFor } from "@/components/site/Offers";

const onNow: PublicOfferRecord = {
  id: "o1",
  name: "Kelewele 2 + 1",
  offerType: "BUY_X_GET_Y",
  summary: "Buy 2 Kelewele, get 1 free",
  schedule: null,
  runningNow: true,
  menuItemIds: ["kelewele"],
};

const happyHour: PublicOfferRecord = {
  id: "o2",
  name: "Happy hour",
  offerType: "PERCENT_OFF",
  summary: "20% off Kelewele",
  schedule: "Fri · 16:00–18:00",
  runningNow: false,
  menuItemIds: ["kelewele"],
  branchName: "Osu",
};

describe("customer offers", () => {
  test("offersFor picks the dish's offers, running ones first", () => {
    expect(offersFor("kelewele", [happyHour, onNow]).map((o) => o.id)).toEqual(["o1", "o2"]);
    expect(offersFor("jollof", [happyHour, onNow])).toEqual([]);
  });

  test("a menu card ribbon shows the best offer, and nothing without one", () => {
    const { rerender } = render(<OfferRibbon offers={[onNow]} />);
    expect(screen.getByText("Buy 2 Kelewele, get 1 free")).toBeInTheDocument();
    rerender(<OfferRibbon offers={[]} />);
    expect(screen.queryByText(/free/)).not.toBeInTheDocument();
  });

  test("the deals strip and dish callout say when an offer runs and where", () => {
    render(<OffersStrip offers={[happyHour, onNow]} />);
    expect(screen.getByRole("heading", { name: /Deals/ })).toBeInTheDocument();
    expect(screen.getByText("Runs Fri · 16:00–18:00 · Osu only")).toBeInTheDocument();
    expect(screen.getByText("On now")).toBeInTheDocument();
  });

  test("a dish page explains the offer is applied at checkout", () => {
    render(<DishOffers offers={[onNow]} />);
    expect(screen.getByText(/Applied automatically at checkout/)).toBeInTheDocument();
  });
});
