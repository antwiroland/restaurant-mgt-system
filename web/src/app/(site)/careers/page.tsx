import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { Icon } from "@/components/Icon";
import { employmentTypeLabel, formatJobDate, paragraphs, requirementItems } from "@/components/site/careers";
import { fetchCareers, getBrand } from "@/lib/publicServer";

export const metadata: Metadata = {
  title: "Careers",
  description: "Open jobs in our kitchens, on the floor and behind the bar.",
};

export default async function CareersPage() {
  await connection();
  const [jobs, brand] = await Promise.all([fetchCareers(), getBrand()]);

  return (
    <div className="site pb-24 pt-10 md:pt-14">
      <header className="max-w-2xl">
        <p className="eyebrow">Careers</p>
        <h1 className="heading-serif mt-3 text-5xl md:text-6xl">Work with us</h1>
        <p className="mt-4 text-lg text-ink-soft">
          We&apos;re a team of cooks, hosts, bartenders and more at {brand.name}. These are the roles we&apos;re hiring for right now.
        </p>
      </header>

      <div className="mt-10">
        {jobs === null ? (
          <div className="rounded-2xl border border-danger-border bg-danger-subtle p-10 text-center text-danger-on" role="alert">
            <p className="font-serif text-2xl">Jobs temporarily unavailable</p>
            <p className="mt-2 text-sm">We couldn&apos;t load our open roles just now. Please try again in a moment.</p>
            <Link href="/careers" className="btn btn-danger mt-5"><Icon name="refresh" size={16} /> Try again</Link>
          </div>
        ) : jobs.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-line-strong bg-surface p-12 text-center">
            <p className="font-serif text-2xl">No open roles right now</p>
            <p className="mt-2 text-sm text-ink-soft">New jobs are posted here as soon as we start hiring. Please check back soon.</p>
          </div>
        ) : (
          <>
            <p className="text-sm font-semibold text-ink-muted">{jobs.length} open {jobs.length === 1 ? "role" : "roles"}</p>
            <ul className="mt-4 grid gap-5">
              {jobs.map((job) => {
                const intro = paragraphs(job.description)[0];
                const needs = requirementItems(job.requirements).slice(0, 3);
                const closes = formatJobDate(job.closingDate);
                return (
                  <li key={job.id}>
                    <article className="rounded-2xl border border-line bg-surface p-6 shadow-xs transition-shadow hover:shadow-md md:p-8">
                      <div className="flex flex-wrap items-start justify-between gap-4">
                        <div className="min-w-0">
                          <h2 className="heading-serif text-2xl md:text-3xl">
                            <Link href={`/careers/${job.id}`} className="hover:text-brand">{job.title}</Link>
                          </h2>
                          <p className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-soft">
                            <span className="inline-flex items-center gap-1.5"><Icon name="pin" size={15} className="text-ink-muted" />{job.branchName}</span>
                            {job.department ? <span className="inline-flex items-center gap-1.5"><Icon name="users" size={15} className="text-ink-muted" />{job.department}</span> : null}
                            {closes ? <span className="inline-flex items-center gap-1.5"><Icon name="calendar" size={15} className="text-ink-muted" />Apply by {closes}</span> : null}
                          </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <span className="badge badge-gold">{employmentTypeLabel[job.employmentType] ?? job.employmentType}</span>
                          {job.openings > 1 ? <span className="badge badge-neutral">{job.openings} positions</span> : null}
                        </div>
                      </div>

                      {intro ? <p className="mt-5 max-w-3xl leading-relaxed text-ink-soft">{intro}</p> : null}

                      {needs.length > 0 ? (
                        <div className="mt-5">
                          <h3 className="text-sm font-bold uppercase tracking-[0.12em] text-ink-muted">Requirements</h3>
                          <ul className="mt-2 grid gap-1.5">
                            {needs.map((need) => (
                              <li key={need} className="flex gap-2 text-ink-soft">
                                <Icon name="check" size={16} className="mt-1 shrink-0 text-brand" />
                                <span>{need}</span>
                              </li>
                            ))}
                          </ul>
                        </div>
                      ) : null}

                      <Link href={`/careers/${job.id}`} className="btn btn-outline mt-6" aria-label={`View the ${job.title} job`}>
                        View job <Icon name="arrowRight" size={16} />
                      </Link>
                    </article>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}
