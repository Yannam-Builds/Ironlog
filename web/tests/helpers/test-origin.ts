import { readFile, realpath } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, resolve, sep } from "node:path";

const contentTypes: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript",
  ".css": "text/css",
  ".json": "application/json",
  ".webmanifest": "application/manifest+json",
  ".png": "image/png",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".ttf": "font/ttf",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
};

// This private, ephemeral origin serves only the existing production build.
// no-store prevents the browser HTTP cache from substituting for the worker.
// It never owns or stops the shared Vite preview server on port 4173.
export async function startTestOrigin() {
  const root = await realpath(resolve(import.meta.dirname, "../../dist"));
  const server = createServer(async (request, response) => {
    response.setHeader("Cache-Control", "no-store");
    try {
      const url = new URL(request.url || "/", "http://127.0.0.1");
      if (!url.pathname.startsWith("/Ironlog/"))
        throw new Error("Outside base");
      const relative = decodeURIComponent(
        url.pathname.slice("/Ironlog/".length),
      );
      if (relative.includes("\\") || relative.includes(":"))
        throw new Error("Invalid path");
      const file = resolve(
        root,
        relative + (relative.endsWith("/") || !relative ? "index.html" : ""),
      );
      if (!file.startsWith(root + sep)) throw new Error("Outside dist");
      const canonicalFile = await realpath(file);
      if (!canonicalFile.startsWith(root + sep))
        throw new Error("Outside dist");
      const body = await readFile(canonicalFile);
      response.writeHead(200, {
        "Content-Type":
          contentTypes[extname(canonicalFile)] || "application/octet-stream",
      });
      response.end(body);
    } catch {
      response.writeHead(404);
      response.end("Not found");
    }
  });
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      server.removeListener("error", reject);
      resolve();
    });
  });
  const address = server.address();
  if (!address || typeof address === "string")
    throw new Error("No test origin address");

  let closing: Promise<void> | undefined;
  return {
    appUrl: `http://127.0.0.1:${address.port}/Ironlog/app/`,
    get listening() {
      return server.listening;
    },
    stop() {
      closing ??= new Promise<void>((resolve, reject) => {
        // Stop accepting connections first, then destroy existing keep-alive
        // sockets. Await closure before any cached-navigation assertion.
        server.close((error) => (error ? reject(error) : resolve()));
        server.closeAllConnections();
      });
      return closing;
    },
  };
}
