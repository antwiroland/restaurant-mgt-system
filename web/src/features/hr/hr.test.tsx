import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { EmployeeForm, validateEmployee } from "@/features/hr/EmployeeForm";
import { hasHrRequirement } from "@/features/hr/HrAccessContext";
import { canAccessPath, navForRole } from "@/lib/navigation";
import type { HrAccess } from "@/lib/hrApi";

const access = (permissions: string[], employeeId: string | null = null): HrAccess => ({ permissions, allBranches: false, branchIds: [], employeeId });
const hrItems = (groups: ReturnType<typeof navForRole>) => groups.find((g) => g.title === "Human Resources")?.items.map((i) => i.href) ?? [];

describe("HR navigation", () => {
  test("givenNoHrAccessLoaded_whenBuildingNav_thenHrItemsHidden", () => {
    expect(hrItems(navForRole("MANAGER"))).toEqual([]);
  });

  test("givenCashierWithoutGrants_whenBuildingNav_thenOnlySelfServiceWhenLinked", () => {
    const unlinked = access([]);
    const linked = access([], "emp-1");
    expect(hrItems(navForRole("CASHIER", (...c) => hasHrRequirement(unlinked, c)))).toEqual([]);
    expect(hrItems(navForRole("CASHIER", (...c) => hasHrRequirement(linked, c)))).toEqual(["/hr/my-schedule", "/hr/me"]);
  });

  test("givenCashierGrantedEmployeeView_whenBuildingNav_thenDirectoryShownButNotAdminAccessPage", () => {
    const granted = access(["hr.employee.view"]);
    expect(hrItems(navForRole("CASHIER", (...c) => hasHrRequirement(granted, c)))).toEqual(["/hr/employees", "/hr/branches", "/hr/organisation"]);
  });

  test("givenAdmin_whenBuildingNav_thenAccessPageShown", () => {
    const admin = access(["hr.employee.view", "hr.department.manage"], "emp-9");
    expect(hrItems(navForRole("ADMIN", (...c) => hasHrRequirement(admin, c)))).toEqual(["/hr/employees", "/hr/branches", "/hr/organisation", "/hr/access", "/hr/my-schedule", "/hr/me"]);
    expect(canAccessPath("MANAGER", "/hr/access")).toBe(false);
    expect(canAccessPath("CASHIER", "/hr/employees/123")).toBe(true); // the page itself enforces HR permissions
  });

  test("givenExistingNavigation_whenBuildingWithoutHr_thenOtherGroupsUnchanged", () => {
    const titles = navForRole("CASHIER").map((g) => g.title);
    expect(titles).toContain("Service");
    expect(titles).not.toContain("Human Resources");
  });
});

describe("Employee form", () => {
  const base = { firstName: "Ama", hireDate: "2025-01-01", email: "", phone: "", dateOfBirth: "", contacts: [] };

  test("givenValidInput_whenValidated_thenNoErrors", () => {
    expect(validateEmployee(base)).toEqual([]);
  });

  test("givenBadInput_whenValidated_thenEachProblemReported", () => {
    const errors = validateEmployee({
      ...base,
      firstName: " ",
      email: "not-an-email",
      phone: "abc",
      dateOfBirth: "2999-01-01",
      contacts: [{ name: "", phone: "" }, { name: "Kojo", phone: "x" }],
    });
    expect(errors).toHaveLength(6);
  });

  test("givenMissingFirstName_whenSubmitted_thenErrorShownAndNothingSent", async () => {
    const onSubmit = vi.fn();
    render(
      <EmployeeForm formId="f" departments={[]} positions={[]} branches={[]} canChangeBranch submitting={false} onSubmit={onSubmit} onCancel={() => undefined} />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Create employee" }));
    expect(screen.getByRole("alert")).toHaveTextContent("First name is required.");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  test("givenNameAndContact_whenSubmitted_thenTrimmedPayloadSent", async () => {
    const onSubmit = vi.fn();
    render(
      <EmployeeForm formId="f" departments={[]} positions={[]} branches={[]} canChangeBranch submitting={false} onSubmit={onSubmit} onCancel={() => undefined} />,
    );
    await userEvent.type(screen.getByLabelText("First name *"), "  Kofi ");
    await userEvent.type(screen.getByLabelText("Last name"), "Mensah");
    await userEvent.click(screen.getByRole("button", { name: /Add contact/ }));
    await userEvent.type(screen.getAllByLabelText("Name")[0], "Esi");
    await userEvent.type(screen.getAllByLabelText("Phone")[1], "+233201234567");
    await userEvent.click(screen.getByRole("button", { name: "Create employee" }));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.firstName).toBe("Kofi");
    expect(payload.lastName).toBe("Mensah");
    expect(payload.employmentType).toBe("FULL_TIME");
    expect(payload.emergencyContacts).toEqual([{ name: "Esi", relationship: undefined, phone: "+233201234567", email: undefined }]);
  });
});
