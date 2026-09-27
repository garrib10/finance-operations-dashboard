import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { useAuth } from "../context/AuthContext";
import { getAccountName } from "../utils/accountIdentity";
import { ACCEPTED_PHOTO_TYPES, MAX_PHOTO_BYTES, photoErrorMessage } from "../utils/profilePhoto";
import { Avatar } from "./Avatar";

type Operation = "idle" | "uploading" | "replacing" | "removing";
interface Selection { file: File; previewUrl: string; }

export function ProfilePhotoSection() {
  const { user, uploadProfilePhoto, removeProfilePhoto } = useAuth();
  const [selection, setSelection] = useState<Selection | null>(null);
  const [previewFailed, setPreviewFailed] = useState(false);
  const [operation, setOperation] = useState<Operation>("idle");
  const [error, setError] = useState("");
  const [invalidSelection, setInvalidSelection] = useState(false);
  const [status, setStatus] = useState("");
  const [focusTarget, setFocusTarget] = useState<{ target: "alert" | "status" } | null>(null);
  const busy = useRef(false);
  const previewUrl = useRef<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const alertRef = useRef<HTMLParagraphElement>(null);
  const statusRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => () => {
    // Object URLs are revoked here on unmount and in replacePreview when replaced.
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = null;
  }, []);

  useEffect(() => {
    if (focusTarget) (focusTarget.target === "alert" ? alertRef : statusRef).current?.focus();
  }, [focusTarget]);

  if (!user) return null;
  const name = getAccountName(user);
  const hasPhoto = Boolean(user.profilePhotoUrl);
  const pending = operation !== "idle";

  function replacePreview(file: File | null) {
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    const next = file ? { file, previewUrl: URL.createObjectURL(file) } : null;
    previewUrl.current = next?.previewUrl ?? null;
    setSelection(next);
    setPreviewFailed(false);
  }

  function clearSelection() {
    replacePreview(null);
    if (inputRef.current) inputRef.current.value = "";
  }

  function showError(message: string, invalid = false) {
    setError(message);
    setInvalidSelection(invalid);
    setStatus("");
    setFocusTarget({ target: "alert" });
  }

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    setError("");
    setInvalidSelection(false);
    setStatus("");
    // A cancelled picker leaves no file; drop any earlier preview so it cannot mislead.
    if (!file) return clearSelection();
    if (!ACCEPTED_PHOTO_TYPES.includes(file.type)) {
      clearSelection();
      return showError("Choose a JPEG or PNG image.", true);
    }
    if (file.size > MAX_PHOTO_BYTES) {
      clearSelection();
      return showError("The photo is too large. Choose an image up to 2 MB.", true);
    }
    replacePreview(file);
  }

  async function run(next: Operation, progress: string, request: () => Promise<unknown>, success: string, action: "upload" | "remove") {
    busy.current = true;
    setOperation(next);
    setError("");
    setInvalidSelection(false);
    setStatus(progress);
    try {
      await request();
      clearSelection();
      setStatus(success);
      setFocusTarget({ target: "status" });
    } catch (failure) {
      showError(photoErrorMessage(failure, action));
    } finally {
      busy.current = false;
      setOperation("idle");
    }
  }

  function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current) return;
    if (!selection) return showError("Choose a JPEG or PNG image to upload.", true);
    const file = selection.file;
    void run(
      hasPhoto ? "replacing" : "uploading",
      hasPhoto ? "Replacing photo…" : "Uploading photo…",
      () => uploadProfilePhoto(file),
      hasPhoto ? "Profile photo replaced." : "Profile photo uploaded.",
      "upload",
    );
  }

  function handleRemove() {
    if (busy.current || !window.confirm("Remove your profile photo?")) return;
    void run("removing", "Removing photo…", removeProfilePhoto, "Profile photo removed.", "remove");
  }

  const uploadLabel = operation === "uploading" ? "Uploading…"
    : operation === "replacing" ? "Replacing…"
    : hasPhoto ? "Replace photo" : "Upload photo";

  return <form className="account-section account-form profile-photo" aria-labelledby="photo-heading" noValidate onSubmit={handleUpload}>
    <h2 id="photo-heading">Profile photo</h2>
    <div className="profile-photo__images">
      <figure className="profile-photo__figure">
        <Avatar className="avatar--large" name={name} photoUrl={user.profilePhotoUrl} />
        <figcaption>Current</figcaption>
      </figure>
      {selection && <figure className="profile-photo__figure">
        {previewFailed
          ? <span className="avatar avatar--large profile-photo__preview-fallback">Preview unavailable</span>
          : <span className="avatar avatar--large">
            <img className="avatar__image" src={selection.previewUrl} alt="Preview of selected photo" onError={() => setPreviewFailed(true)} />
          </span>}
        <figcaption>Selected</figcaption>
      </figure>}
    </div>
    <div className="form-field">
      <label htmlFor="profile-photo-input">Choose a photo</label>
      <input ref={inputRef} id="profile-photo-input" name="photo" type="file" accept="image/jpeg,image/png"
        disabled={pending} aria-invalid={invalidSelection}
        aria-describedby={`profile-photo-help${invalidSelection ? " profile-photo-error" : ""}`}
        onChange={handleChange} />
      <p id="profile-photo-help" className="profile-photo__help">Upload a JPEG or PNG image up to 2 MB.</p>
    </div>
    <p ref={alertRef} id="profile-photo-error" role="alert" tabIndex={-1} className="form-error">{error}</p>
    <p ref={statusRef} role="status" tabIndex={-1}>{status}</p>
    <div className="account-actions">
      <button className="button button--primary" disabled={pending}>{uploadLabel}</button>
      {hasPhoto && <button type="button" className="button button--danger" disabled={pending} onClick={handleRemove}>
        {operation === "removing" ? "Removing…" : "Remove photo"}
      </button>}
    </div>
  </form>;
}
