"use client";

import { useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { EmptyState, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatBytes, formatDate, todayIso } from "@/features/hr/ui";
import { saveDownloadedFile } from "@/lib/apiClient";
import { DOCUMENT_TYPES, archiveDocument, downloadDocument, uploadDocument, type DocumentType, type EmployeeDocument } from "@/lib/hrApi";

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT = ".pdf,.png,.jpg,.jpeg,.docx,application/pdf,image/png,image/jpeg,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

export function DocumentsPanel({ employeeId, documents, showArchived, onToggleArchived, onChanged }: {
  employeeId: string; documents: EmployeeDocument[]; showArchived: boolean; onToggleArchived: (value: boolean) => void; onChanged: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const canManage = can("hr.document.manage");

  async function download(doc: EmployeeDocument) {
    setBusyId(doc.id);
    setError("");
    try {
      saveDownloadedFile(await authenticatedFetch((s) => downloadDocument(s, doc.id, doc.fileName)));
    } catch (err) {
      setError(errorMessage(err, "Download failed"));
    } finally {
      setBusyId(null);
    }
  }

  async function archive(doc: EmployeeDocument) {
    setBusyId(doc.id);
    setError("");
    try {
      await authenticatedFetch((s) => archiveDocument(s, doc.id));
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not archive the document"));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="grid gap-4">
      <SectionHeader
        title="Documents"
        description="Stored privately. Every download is recorded in the employee's history."
        actions={(
          <>
            <label className="flex items-center gap-2 text-sm text-ink-soft">
              <input type="checkbox" checked={showArchived} onChange={(e) => onToggleArchived(e.target.checked)} /> Show archived
            </label>
            {canManage ? (
              <button type="button" className="btn btn-primary btn-sm" onClick={() => setUploading(true)}>
                <Icon name="upload" size={16} /> Upload
              </button>
            ) : null}
          </>
        )}
      />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {documents.length === 0 ? (
        <EmptyState compact icon="clipboard" title="No documents" description={canManage ? "Upload contracts, IDs, CVs and certificates (PDF, PNG, JPEG or DOCX, up to 5 MB)." : undefined} />
      ) : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Document</th><th>Type</th><th>Expires</th><th>Uploaded</th><th className="num">Actions</th></tr></thead>
            <tbody>
              {documents.map((d) => {
                const expired = d.expiresOn ? d.expiresOn < todayIso() : false;
                return (
                  <tr key={d.id} className={d.archived ? "opacity-60" : ""}>
                    <td><span className="font-semibold">{d.title}</span><p className="text-xs text-ink-muted">{d.fileName} · {formatBytes(d.sizeBytes)}{d.archived ? " · archived" : ""}</p></td>
                    <td>{humanize(d.documentType)}</td>
                    <td className={expired ? "font-semibold text-danger" : ""}>{d.expiresOn ? `${formatDate(d.expiresOn)}${expired ? " (expired)" : ""}` : "—"}</td>
                    <td className="tabular">{formatDate(d.uploadedAt)}</td>
                    <td className="num">
                      <div className="flex justify-end gap-1">
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => download(d)} disabled={busyId === d.id} aria-label={`Download ${d.title}`}>
                          <Icon name="download" size={16} />
                        </button>
                        {canManage && !d.archived ? (
                          <button type="button" className="btn btn-ghost btn-sm" onClick={() => archive(d)} disabled={busyId === d.id} aria-label={`Archive ${d.title}`}>
                            <Icon name="trash" size={16} />
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {uploading ? <UploadModal employeeId={employeeId} onClose={() => setUploading(false)} onDone={() => { setUploading(false); onChanged(); }} /> : null}
    </section>
  );
}

function UploadModal({ employeeId, onClose, onDone }: { employeeId: string; onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [type, setType] = useState<DocumentType>("EMPLOYMENT_CONTRACT");
  const [expiresOn, setExpiresOn] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) return setError("Choose a file.");
    if (file.size > MAX_BYTES) return setError("Files must be 5 MB or smaller.");
    if (!title.trim()) return setError("Give the document a title.");
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => uploadDocument(s, employeeId, { file, title: title.trim(), documentType: type, expiresOn: expiresOn || undefined, notes: notes.trim() || undefined }));
      onDone();
    } catch (err) {
      setError(errorMessage(err, "Upload failed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title="Upload document"
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="upload-form" className="btn btn-primary btn-md" disabled={busy}>{busy ? "Uploading…" : "Upload"}</button>
        </>
      )}
    >
      <form id="upload-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="File *" hint="PDF, PNG, JPEG or DOCX, up to 5 MB" className="sm:col-span-2">
          <input
            type="file"
            className="input"
            accept={ACCEPT}
            onChange={(e) => {
              const chosen = e.target.files?.[0] ?? null;
              setFile(chosen);
              if (chosen && !title) setTitle(chosen.name.replace(/\.[^.]+$/, ""));
            }}
          />
        </Field>
        <Field label="Title *"><input className="input" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} /></Field>
        <Field label="Type">
          <select className="select" value={type} onChange={(e) => setType(e.target.value as DocumentType)}>
            {DOCUMENT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </Field>
        <Field label="Expires on" hint="For certificates and IDs"><input type="date" className="input" value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} /></Field>
        <Field label="Notes"><input className="input" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} /></Field>
      </form>
    </Modal>
  );
}
