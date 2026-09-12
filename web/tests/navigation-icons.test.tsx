import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Icon } from "../src/ui/components";

const composePaths = {
  home: "M12,5.69l5,4.5V18h-2v-6H9v6H7v-7.81l5,-4.5M12,3L2,12h3v8h6v-6h2v6h6v-8h3L12,3z",
  plans: "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 5.57,4.14 4.14,2.71 2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 15.57,17 12,20.57 13.43,22l1.43,-1.43L16.29,22l2.14,-2.14 1.43,1.43 1.43,-1.43 -1.43,-1.43L22,16.29l-1.43,-1.43z",
  stats: "M3.5,18.49l6,-6.01 4,4L22,6.92l-1.41,-1.41 -7.09,7.97 -4,-4L2,16.99l1.5,1.5z",
};

describe("Kotlin navigation icon parity", () => {
  it.each(Object.entries(composePaths))("renders the exact Compose %s vector", (name, path) => {
    const { container } = render(<Icon name={name} size={21} />);
    const svg = container.querySelector("svg");
    const vector = container.querySelector("path");
    expect(svg).toHaveAttribute("width", "21");
    expect(svg).toHaveAttribute("height", "21");
    expect(vector).toHaveAttribute("d", path);
    expect(vector).toHaveAttribute("fill", "currentColor");
    expect(vector).toHaveAttribute("stroke", "none");
  });
});
