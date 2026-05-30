import { createServer } from "http";
import { readFile } from "fs/promises";
import { join, extname, resolve } from "path";

const PORT = 3100;
const DIR = resolve(import.meta.dirname, "../spa/spa-client");

const MIME = {
  ".html": "text/html",
  ".js": "application/javascript",
  ".css": "text/css",
  ".json": "application/json",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
};

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  let path = url.pathname;
  if (path === "/") path = "/index.html";
  try {
    const data = await readFile(join(DIR, path));
    res.writeHead(200, { "Content-Type": MIME[extname(path)] || "application/octet-stream" });
    res.end(data);
  } catch {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end("404 Not Found");
  }
}).listen(PORT, () => console.log(`spa-client serving on http://localhost:${PORT}`));
