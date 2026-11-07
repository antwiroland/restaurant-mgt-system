import { render, screen, within } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { BillBreakdown } from "@/components/BillBreakdown";
import { addDays, formatMinutes, isoDate, sameDay, startOfWeek } from "@/features/workforce/time";

describe("Bill breakdown", () => {
  test("givenTaxesServiceChargeAndTip_whenRendered_thenEachLineAndTotalShown", () => {
    render(
      <BillBreakdown
        subtotal="100.00"
        discountTotal="10.00"
        serviceCharge="9.00"
        taxLines={[
          { code: "NHIL", name: "NHIL", rate: "2.50", amount: "2.48" },
          { code: "VAT", name: "VAT", rate: "15.00", amount: "15.25" },
        ]}
        taxTotal="17.73"
        tipAmount="5.00"
        total="121.73"
      />,
    );
    const bill = screen.getByLabelText("Bill breakdown");
    expect(within(bill).getByText("Discounts")).toBeInTheDocument();
    expect(within(bill).getByText("- GHS 10.00")).toBeInTheDocument();
    expect(within(bill).getByText("Service charge")).toBeInTheDocument();
    expect(within(bill).getByText("NHIL (2.5%)")).toBeInTheDocument();
    expect(within(bill).getByText("VAT (15%)")).toBeInTheDocument();
    expect(within(bill).getByText("Tip")).toBeInTheDocument();
    expect(within(bill).getByText("GHS 121.73")).toBeInTheDocument();
  });

  test("givenTaxInclusivePrices_whenRendered_thenTaxMarkedIncludedAndZeroLinesHidden", () => {
    render(
      <BillBreakdown
        subtotal="115.00"
        taxLines={[{ code: "VAT", name: "VAT", rate: "15", amount: "15.00" }]}
        taxTotal="15.00"
        taxInclusive
        serviceCharge="0"
        tipAmount="0"
        total="115.00"
      />,
    );
    expect(screen.getByText("VAT (15%) incl.")).toBeInTheDocument();
    expect(screen.getByText("Prices include GHS 15.00 of tax.")).toBeInTheDocument();
    expect(screen.queryByText("Service charge")).not.toBeInTheDocument();
    expect(screen.queryByText("Tip")).not.toBeInTheDocument();
  });
});

describe("Rota time helpers", () => {
  test("givenMidweekDate_whenStartOfWeek_thenMondayAtMidnight", () => {
    const monday = startOfWeek(new Date(2026, 10, 5, 15, 30)); // Thursday 5 Nov 2026
    expect(monday.getDay()).toBe(1);
    expect(isoDate(monday)).toBe("2026-11-02");
    expect(monday.getHours()).toBe(0);
  });

  test("givenSunday_whenStartOfWeek_thenPreviousMonday", () => {
    expect(isoDate(startOfWeek(new Date(2026, 10, 8, 9)))).toBe("2026-11-02");
  });

  test("givenMinutes_whenFormatted_thenHoursAndPaddedMinutes", () => {
    expect(formatMinutes(125)).toBe("2h 05m");
    expect(formatMinutes(0)).toBe("0h 00m");
    expect(formatMinutes(-30)).toBe("-0h 30m");
  });

  test("givenDatesAcrossWeek_whenCompared_thenSameDayOnlyMatchesCalendarDay", () => {
    const start = new Date(2026, 10, 2, 8);
    expect(sameDay(start, new Date(2026, 10, 2, 23, 59))).toBe(true);
    expect(sameDay(start, addDays(start, 1))).toBe(false);
  });
});
