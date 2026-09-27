import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Avatar } from "./Avatar";
import { photoUrl, replacementPhotoUrl } from "../test/accountFixtures";

describe("Avatar", () => {
  it("renders a valid HTTPS photo with context-appropriate alternative text", () => {
    render(<Avatar name="Demo User" photoUrl={photoUrl} className="avatar--large" />);
    const image = screen.getByRole("img", { name: "Profile photo for Demo User" });
    expect(image).toHaveAttribute("src", photoUrl);
    expect(image.parentElement).toHaveClass("avatar", "avatar--large");
    expect(screen.queryByText("DU")).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(photoUrl);
  });

  it.each([null, undefined, "", "http://insecure.example/a.jpg", "not a url", "javascript:alert(1)"])("renders initials for %s", (value) => {
    const { container } = render(<Avatar name="Demo User" photoUrl={value} />);
    expect(screen.getByRole("img", { name: "Initials for Demo User" })).toHaveTextContent("DU");
    expect(container.querySelector("img")).toBeNull();
  });

  it("replaces a failed image with initials and retries when the URL changes", () => {
    const { container, rerender } = render(<Avatar name="Demo User" photoUrl={photoUrl} />);
    fireEvent.error(screen.getByRole("img", { name: "Profile photo for Demo User" }));
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByRole("img", { name: "Initials for Demo User" })).toHaveTextContent("DU");

    rerender(<Avatar name="Demo User" photoUrl={replacementPhotoUrl} />);
    expect(screen.getByRole("img", { name: "Profile photo for Demo User" })).toHaveAttribute("src", replacementPhotoUrl);

    rerender(<Avatar name="Demo User" photoUrl={photoUrl} />);
    expect(screen.getByRole("img", { name: "Profile photo for Demo User" })).toHaveAttribute("src", photoUrl);
  });

  it("hides decorative photos and initials from assistive technology", () => {
    const { container, rerender } = render(<Avatar name="Demo User" photoUrl={photoUrl} decorative />);
    expect(container.querySelector("img")).toHaveAttribute("alt", "");
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
    expect(screen.queryByRole("img")).not.toBeInTheDocument();

    rerender(<Avatar name="Demo User" photoUrl={null} decorative />);
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
    expect(container.firstElementChild).not.toHaveAttribute("role");
    expect(container).toHaveTextContent("DU");
  });
});
