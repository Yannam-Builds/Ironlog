import { test, expect } from "@playwright/test";
// Engine diagnostic, not an application acceptance contract. WebKit on this host
// rejects native Blobs, so production storage uses bytes. Keep the failing raw
// capability evidence separate from the actual photo backup acceptance test.

const png = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jD1sAAAAASUVORK5CYII=",
  "base64",
);

test("diagnose native File versus Blob IndexedDB persistence", async ({
  page,
}, info) => {
  await page.goto("app/");
  await expect(page.getByLabel("Your name")).toBeVisible();
  await page.evaluate(() => {
    const input = document.createElement("input");
    input.type = "file";
    input.id = "diagnostic-upload";
    document.body.append(input);
  });
  await page
    .locator("#diagnostic-upload")
    .setInputFiles({
      name: "synthetic.png",
      mimeType: "image/png",
      buffer: png,
    });
  const result = await page.evaluate(async () => {
    const file = (
      document.querySelector("#diagnostic-upload") as HTMLInputElement
    ).files![0];
    const timeout = <T>(
      promise: Promise<T>,
      label: string,
    ): Promise<T | string> =>
      Promise.race([
        promise,
        new Promise<string>((resolve) =>
          setTimeout(() => resolve(`${label}:timed-out`), 4000),
        ),
      ]);
    const bufferResult = await timeout(file.arrayBuffer(), "file-read");
    const variants = [
      { name: "native-file", value: file },
      {
        name: "memory-blob",
        value: new Blob([new Uint8Array([137, 80, 78, 71])], {
          type: "image/png",
        }),
      },
      {
        name: "memory-file",
        value: new File([new Uint8Array([137, 80, 78, 71])], "memory.png", {
          type: "image/png",
        }),
      },
      { name: "uint8array", value: new Uint8Array([137, 80, 78, 71]) },
    ];
    const writes = await Promise.all(
      variants.map(async (variant) => {
        const events: string[] = [];
        const result = await timeout(
          new Promise<string>((resolve) => {
            const open = indexedDB.open(
              `ironlog-photo-diagnostic-${variant.name}`,
            );
            open.onupgradeneeded = () =>
              open.result.createObjectStore("photos", { keyPath: "id" });
            open.onerror = () => resolve(`open:${open.error}`);
            open.onsuccess = () => {
              const db = open.result;
              const tx = db.transaction("photos", "readwrite");
              tx.oncomplete = () => {
                events.push("transaction-complete");
                db.close();
                resolve("saved");
              };
              tx.onabort = () => {
                db.close();
                resolve(`abort:${tx.error}`);
              };
              tx.onerror = () => events.push(`transaction-error:${tx.error}`);
              try {
                const request = tx
                  .objectStore("photos")
                  .put({ id: "synthetic", blob: variant.value });
                request.onsuccess = () => events.push("request-success");
                request.onerror = () =>
                  events.push(`request-error:${request.error}`);
              } catch (error) {
                db.close();
                resolve(`throw:${error}`);
              }
            };
          }),
          variant.name,
        );
        return { variant: variant.name, result, events };
      }),
    );
    return {
      file: { size: file.size, type: file.type, name: file.name },
      read:
        typeof bufferResult === "string"
          ? bufferResult
          : bufferResult.byteLength,
      writes,
    };
  });
  console.log(JSON.stringify({ browser: info.project.name, result }));
  await info.attach("native-photo-persistence", {
    body: JSON.stringify(result, null, 2),
    contentType: "application/json",
  });
  expect(result.writes.map((write) => [write.variant, write.result])).toEqual([
    ["native-file", "saved"],
    ["memory-blob", "saved"],
    ["memory-file", "saved"],
    ["uint8array", "saved"],
  ]);
});
