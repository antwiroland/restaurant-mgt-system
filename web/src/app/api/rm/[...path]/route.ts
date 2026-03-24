import { NextRequest, NextResponse } from "next/server";

const BACKEND_BASE_URL = process.env.RESTAURANT_MANAGER_BACKEND_URL ?? "http://localhost:8080";

async function proxy(request: NextRequest, path: string[]): Promise<NextResponse> {
  const search = request.nextUrl.search || "";
  const targetUrl = `${BACKEND_BASE_URL}/${path.join("/")}${search}`;
  const headers = new Headers();

  const contentType = request.headers.get("content-type");
  const authorization = request.headers.get("authorization");

  if (contentType) {
    headers.set("content-type", contentType);
  }
  if (authorization) {
    headers.set("authorization", authorization);
  }

  const init: RequestInit = {
    method: request.method,
    headers,
    cache: "no-store",
  };

  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = await request.text();
  }

  const response = await fetch(targetUrl, init);
  const body = await response.text();

  return new NextResponse(body, {
    status: response.status,
    headers: {
      "content-type": response.headers.get("content-type") ?? "application/json",
    },
  });
}

type Params = { params: Promise<{ path: string[] }> };

export async function GET(request: NextRequest, context: Params) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function POST(request: NextRequest, context: Params) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function PATCH(request: NextRequest, context: Params) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function DELETE(request: NextRequest, context: Params) {
  const { path } = await context.params;
  return proxy(request, path);
}
