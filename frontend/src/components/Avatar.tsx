import { useState } from "react";
import { getInitials, getSafePhotoUrl } from "../utils/accountIdentity";

interface AvatarProps {
  name: string;
  photoUrl: string | null | undefined;
  className?: string;
  /** Decorative avatars sit beside visible account text and are hidden from assistive technology. */
  decorative?: boolean;
}

interface AvatarContentProps extends Omit<AvatarProps, "photoUrl"> {
  src: string | null;
}

export function Avatar({ photoUrl, ...props }: AvatarProps) {
  const src = getSafePhotoUrl(photoUrl);
  // Keying by URL resets the load-failure state whenever a different photo is supplied.
  return <AvatarContent key={src ?? ""} src={src} {...props} />;
}

function AvatarContent({ name, src, className, decorative = false }: AvatarContentProps) {
  const [failed, setFailed] = useState(false);
  const classes = ["avatar", className].filter(Boolean).join(" ");

  if (src && !failed) {
    return <span className={classes} aria-hidden={decorative || undefined}>
      <img className="avatar__image" src={src} alt={decorative ? "" : `Profile photo for ${name}`}
        onError={() => setFailed(true)} />
    </span>;
  }

  return <span className={`${classes} avatar--initials`}
    {...(decorative ? { "aria-hidden": true } : { role: "img", "aria-label": `Initials for ${name}` })}>
    {getInitials(name)}
  </span>;
}
