import type { RefObject } from "react";

interface AccountFeedbackProps {
  failure: string;
  success: string;
  alertRef: RefObject<HTMLParagraphElement | null>;
  statusRef: RefObject<HTMLParagraphElement | null>;
}

export function AccountFeedback({ failure, success, alertRef, statusRef }: AccountFeedbackProps) {
  return <>
    <p ref={alertRef} role="alert" tabIndex={-1} className="form-error">{failure}</p>
    <p ref={statusRef} role="status" tabIndex={-1}>{success}</p>
  </>;
}
