import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "Manager", role: "MANAGER" } };

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));
vi.mock("@/features/hr/HrAccessContext", () => ({
  useHrAccess: () => ({ access: { allBranches: false, branchIds: ["b1"] }, loading: false, can: () => true }),
}));

const api = vi.hoisted(() => ({ getApplication: vi.fn(), moveApplication: vi.fn(), hireApplicant: vi.fn(), getVacancy: vi.fn(), setVacancyPublished: vi.fn() }));
vi.mock("@/lib/hrApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/hrApi")>()), ...api }));
vi.mock("@/lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/apiClient")>()),
  getSchedulableStaff: vi.fn().mockResolvedValue([{ id: "s1", name: "Head Chef", role: "MANAGER" }]),
}));

import { ApplicationDrawer } from "@/features/hr/recruitment/ApplicationDrawer";
import { VacancyDrawer } from "@/features/hr/recruitment/VacancyDrawer";
import { nextStages } from "@/features/hr/recruitment/recruitUtils";
import type { ApplicationDetail, VacancyDetail } from "@/lib/hrApi";

function application(overrides: Partial<ApplicationDetail> = {}): ApplicationDetail {
  return {
    id: "a1", stage: "SCREENING", candidate: { id: "c1", fullName: "Akosua Mensah", phone: "+233240000111", source: "WALK_IN", openApplications: 1,
      applications: 1, anonymised: false, createdAt: "2026-11-01T10:00:00Z" },
    vacancy: { id: "v1", vacancyNumber: "VAC-000001", title: "Line cook", status: "OPEN", branchId: "b1", branchName: "Osu" },
    events: [{ toStage: "APPLIED", at: "2026-11-01T10:00:00Z" }, { fromStage: "APPLIED", toStage: "SCREENING", at: "2026-11-02T10:00:00Z", actorName: "Manager" }],
    interviews: [], documents: [], appliedAt: "2026-11-01T10:00:00Z", payVisible: false, canHire: true, canCreateContract: false,
    ...overrides,
  };
}

describe("recruitment", () => {
  beforeEach(() => Object.values(api).forEach((fn) => fn.mockReset()));

  test("givenStages_whenComputingNextMoves_thenOnlyForwardOrReconsider", () => {
    expect(nextStages("APPLIED")).toEqual(["SCREENING", "INTERVIEW", "TRIAL_SHIFT", "OFFER"]);
    expect(nextStages("OFFER")).toEqual([]);
    expect(nextStages("REJECTED")).toEqual(["SCREENING"]);
    expect(nextStages("HIRED")).toEqual([]);
  });

  test("givenScreeningApplication_whenRejectingWithoutNote_thenRejectDisabled_andMoveSendsStage", async () => {
    api.getApplication.mockResolvedValue(application());
    api.moveApplication.mockResolvedValue(application({ stage: "INTERVIEW" }));
    render(<ApplicationDrawer id="a1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByRole("button", { name: "Reject" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Screening" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Interview" }));
    expect(await screen.findByText("Moved to interview.")).toBeInTheDocument();
    expect(api.moveApplication.mock.calls[0][2]).toMatchObject({ stage: "INTERVIEW" });
  });

  test("givenAcceptedOfferWithHiddenPay_whenShown_thenPayHiddenAndHireOffered", async () => {
    api.getApplication.mockResolvedValue(application({
      stage: "OFFER",
      offer: { contractType: "PERMANENT", payHidden: true, startDate: "2026-12-01", status: "ACCEPTED" },
    }));
    api.hireApplicant.mockResolvedValue({ employeeId: "e1", employeeNumber: "EMP-000042", contractCreated: false,
      contractNote: "Someone who manages contracts must create the contract", documentsCopied: 1 });
    render(<ApplicationDrawer id="a1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByText("Hidden (no pay permission)")).toBeInTheDocument();
    expect(screen.getByText("The contract will need to be created by someone who manages contracts.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Hire — create employee record" }));
    expect(await screen.findByText(/Hired as EMP-000042\. 1 document\(s\) copied\./)).toBeInTheDocument();
  });

  test("givenVacancy_whenOpened_thenPipelineGroupsApplicantsByStage", async () => {
    const vacancy: VacancyDetail = {
      summary: {
        id: "v1", vacancyNumber: "VAC-000001", branchId: "b1", branchName: "Osu", title: "Line cook", employmentType: "FULL_TIME", openings: 2,
        hiredCount: 0, payVisible: false, status: "OPEN", daysOpen: 3, version: 1, openedAt: "2026-11-01T10:00:00Z", published: false,
        pipeline: { APPLIED: 1, SCREENING: 0, INTERVIEW: 1, TRIAL_SHIFT: 0, OFFER: 0, HIRED: 0, REJECTED: 1, WITHDRAWN: 0 },
      },
      applications: [
        { id: "a1", candidateId: "c1", candidateName: "Akosua Mensah", source: "WALK_IN", stage: "APPLIED", appliedAt: "2026-11-01T10:00:00Z" },
        { id: "a2", candidateId: "c2", candidateName: "Kojo Asare", source: "REFERRAL", stage: "INTERVIEW", averageScore: 4, appliedAt: "2026-11-01T10:00:00Z" },
        { id: "a3", candidateId: "c3", candidateName: "Rejected Person", source: "OTHER", stage: "REJECTED", appliedAt: "2026-11-01T10:00:00Z" },
      ],
    };
    api.getVacancy.mockResolvedValue(vacancy);
    render(<VacancyDrawer id="v1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByText("Akosua Mensah")).toBeInTheDocument();
    expect(screen.getByText("Kojo Asare")).toBeInTheDocument();
    expect(screen.getByText(/score 4/)).toBeInTheDocument();
    expect(screen.queryByText("Rejected Person")).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("Show hired, rejected and withdrawn"));
    expect(screen.getByText("Rejected Person")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Close vacancy" })).toBeDisabled(); // needs a reason
  });

  test("givenOpenVacancy_whenPublishedThenUnpublished_thenCareersPageStateFollows", async () => {
    const base: VacancyDetail = {
      summary: {
        id: "v1", vacancyNumber: "VAC-000001", branchId: "b1", branchName: "Osu", title: "Line cook", employmentType: "FULL_TIME", openings: 1,
        hiredCount: 0, payVisible: false, status: "OPEN", daysOpen: 1, version: 1, published: false,
        pipeline: { APPLIED: 0, SCREENING: 0, INTERVIEW: 0, TRIAL_SHIFT: 0, OFFER: 0, HIRED: 0, REJECTED: 0, WITHDRAWN: 0 },
      },
      description: "Grill and stew station.",
      requirements: "Food handler certificate",
      applications: [],
    };
    const live: VacancyDetail = { ...base, summary: { ...base.summary, published: true, publishedAt: "2026-11-12T09:00:00Z" } };
    api.getVacancy.mockResolvedValue(base);
    api.setVacancyPublished.mockResolvedValueOnce(live).mockResolvedValueOnce(base);
    render(<VacancyDrawer id="v1" onClose={() => undefined} onChanged={() => undefined} />);

    expect(await screen.findByText(/Grill and stew station/)).toBeInTheDocument();
    expect(screen.getByText(/Not published/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Publish to careers page" }));
    expect(api.setVacancyPublished).toHaveBeenLastCalledWith(expect.anything(), "v1", true);
    expect(await screen.findByRole("link", { name: "View posting" })).toHaveAttribute("href", "/careers/v1");
    expect(screen.getAllByText("On careers page").length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole("button", { name: "Unpublish" }));
    expect(api.setVacancyPublished).toHaveBeenLastCalledWith(expect.anything(), "v1", false);
    expect(await screen.findByRole("button", { name: "Publish to careers page" })).toBeInTheDocument();
    expect(screen.queryByText("On careers page")).not.toBeInTheDocument();
  });
});
