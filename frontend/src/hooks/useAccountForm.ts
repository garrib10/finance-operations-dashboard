import { useEffect, useRef, useState } from "react";
import { ApiError } from "../services/api";

export function useAccountForm() {
  const [pending, setPending] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState("");
  const [success, setSuccess] = useState("");
  const busy = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const statusRef = useRef<HTMLParagraphElement>(null);
  const alertRef = useRef<HTMLParagraphElement>(null);
  const [failureAttempt, setFailureAttempt] = useState(0);

  useEffect(() => {
    if (failureAttempt) {
      const invalid = formRef.current?.querySelector<HTMLElement>("[aria-invalid=true]");
      (invalid ?? alertRef.current)?.focus();
    }
  }, [failureAttempt]);

  useEffect(() => {
    if (success) statusRef.current?.focus();
  }, [success]);

  function clear(field?: string) {
    setSuccess("");
    setFailure("");
    setErrors(previous => field ? { ...previous, [field]: "" } : {});
  }

  async function submit(
    validation: Record<string, string>,
    save: () => Promise<void>,
    message: string,
    allowedFields: readonly string[],
  ) {
    if (busy.current) return;
    clear();
    if (Object.keys(validation).length) {
      setErrors(validation);
      setFailure("Please correct the highlighted fields.");
      setFailureAttempt(value => value + 1);
      return;
    }
    busy.current = true;
    setPending(true);
    try {
      await save();
      setSuccess(message);
    } catch (error) {
      // Only trusted validation fields from a 400 response belong beside controls.
      const fields = error instanceof ApiError && error.status === 400
        ? error.validationErrors : undefined;
      setErrors(Object.fromEntries(allowedFields
        .filter(field => fields?.[field])
        .map(field => [field, fields![field]])));
      setFailure(fields ? "Please check your entries and try again." : "Unable to save changes. Please try again.");
      setFailureAttempt(value => value + 1);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return { pending, errors, failure, success, formRef, statusRef, alertRef, clear, submit };
}
