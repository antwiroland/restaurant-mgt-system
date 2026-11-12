import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Icon } from "@/components/Icon";
import { employmentTypeLabel, formatJobDate, paragraphs, requirementItems } from "@/components/site/careers";
import { fetchCareer, getBrand } from "@/lib/publicServer";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const job = await fetchCareer(id);
  return job ? { title: `${job.title} – Careers`, description: paragraphs(job.description)[0] ?? `${job.title} at ${job.branchName}` } : { title: "Job" };
}

export default async function JobPage({ params }: Props) {
  await connection();
  const { id } = await params;
  const [job, brand] = await Promise.all([fetchCareer(id), getBrand()]);
  if (!job) notFound();

  const description = paragraphs(job.description);
  const requirements = requirementItems(job.requirements);
  const closes = formatJobDate(job.closingDate);
  const posted = formatJobDate(job.postedAt);
  const subject = encodeURIComponent(`Application: ${job.title} (${job.reference})`);

  return (
    <div className="pb-24">
      <div className="site pt-6">
        <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-sm text-ink-muted">
          <Link href="/careers" className="hover:text-brand">Careers</Link>
          <Icon name="chevronRight" size={14} />
          <span className="truncate text-ink-soft" aria-current="page">{job.title}</span>
        </nav>
      </div>

      <section className="site mt-6 grid gap-10 lg:grid-cols-[1fr_340px] lg:gap-14">
        <article className="min-w-0">
          <p className="eyebrow">{job.department ?? "Careers"}</p>
          <h1 className="heading-serif mt-3 text-4xl leading-tight md:text-5xl">{job.title}</h1>
          <div className="mt-5 flex flex-wrap items-center gap-2">
            <span className="badge badge-gold">{employmentTypeLabel[job.employmentType] ?? job.employmentType}</span>
            <span className="badge badge-neutral">{job.branchName}</span>
            {job.openings > 1 ? <span className="badge badge-neutral">{job.openings} positions</span> : null}
          </div>

          <h2 className="heading-serif mt-10 text-2xl">About the role</h2>
          {description.length > 0 ? (
            <div className="mt-3 grid max-w-3xl gap-4 text-lg leading-relaxed text-ink-soft">
              {description.map((part) => <p key={part} className="whitespace-pre-line">{part}</p>)}
            </div>
          ) : (
            <p className="mt-3 text-ink-soft">Ask us about this role. We&apos;ll gladly tell you more.</p>
          )}

          <h2 className="heading-serif mt-10 text-2xl">Requirements</h2>
          {requirements.length > 0 ? (
            <ul className="mt-3 grid max-w-3xl gap-2.5">
              {requirements.map((need) => (
                <li key={need} className="flex gap-3 text-lg text-ink-soft">
                  <Icon name="checkCircle" size={20} className="mt-1 shrink-0 text-brand" />
                  <span>{need}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-3 text-ink-soft">No specific requirements. A good attitude and willingness to learn go a long way.</p>
          )}
        </article>

        <aside className="lg:pt-2">
          <div className="rounded-2xl border border-line bg-surface p-6 shadow-xs lg:sticky lg:top-24">
            <h2 className="font-serif text-xl font-semibold">Job details</h2>
            <dl className="mt-4 grid gap-3 text-sm">
              <div className="flex justify-between gap-4"><dt className="text-ink-muted">Location</dt><dd className="text-right font-semibold">{job.branchName}</dd></div>
              <div className="flex justify-between gap-4"><dt className="text-ink-muted">Type</dt><dd className="text-right font-semibold">{employmentTypeLabel[job.employmentType] ?? job.employmentType}</dd></div>
              {posted ? <div className="flex justify-between gap-4"><dt className="text-ink-muted">Posted</dt><dd className="text-right font-semibold">{posted}</dd></div> : null}
              {closes ? <div className="flex justify-between gap-4"><dt className="text-ink-muted">Apply by</dt><dd className="text-right font-semibold">{closes}</dd></div> : null}
              <div className="flex justify-between gap-4"><dt className="text-ink-muted">Reference</dt><dd className="text-right font-semibold">{job.reference}</dd></div>
            </dl>

            <h3 className="mt-6 font-semibold">How to apply</h3>
            <p className="mt-1 text-sm text-ink-soft">
              Send us your CV and quote reference {job.reference}, or drop it off at {job.branchName}.
            </p>
            <div className="mt-4 grid gap-2">
              {brand.email ? (
                <a href={`mailto:${brand.email}?subject=${subject}`} className="btn btn-primary btn-block"><Icon name="mail" size={16} /> Email your CV</a>
              ) : null}
              {brand.phone ? (
                <a href={`tel:${brand.phone.replace(/\s+/g, "")}`} className="btn btn-outline btn-block"><Icon name="phone" size={16} /> Call {brand.phone}</a>
              ) : null}
            </div>
          </div>
          <Link href="/careers" className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-ink-soft hover:text-brand">
            <Icon name="arrowLeft" size={16} /> All open roles
          </Link>
        </aside>
      </section>
    </div>
  );
}
