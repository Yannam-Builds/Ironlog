import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { BodyMap } from "../src/ui/BodyMap";

afterEach(cleanup);

it.each(["front", "back"] as const)("keeps the %s body centered in its canonical viewBox and opens region evidence", (side) => {
  const select = vi.fn(); const { container } = render(<div style={{ width: 640, height: 220, fontSize: 24 }}><BodyMap side={side} scores={{ Push: 80 }} onSelect={select} /></div>);
  const svg = screen.getByRole("img", { name: new RegExp(`^${side} muscle`) });
  expect(svg).toHaveAttribute("preserveAspectRatio", "xMidYMin meet");
  expect(svg.getAttribute("viewBox")?.split(" ").map(Number)).toHaveLength(4);
  expect(svg.getAttribute("viewBox")?.split(" ").map(Number).every(Number.isFinite)).toBe(true);
  const target = screen.getAllByRole("button")[0], region = target.getAttribute("aria-label")!.replace("Open ", "").replace(" recovery evidence", "");
  fireEvent.click(target); fireEvent.keyDown(target, { key: "Enter" });
  expect(select).toHaveBeenNthCalledWith(1, region); expect(select).toHaveBeenNthCalledWith(2, region);
  expect(container.querySelector(".body-map")?.getAttribute("style")).toBeNull();
});
