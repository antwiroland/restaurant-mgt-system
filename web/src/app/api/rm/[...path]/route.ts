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

  let response: Response;
  try {
    response = await fetch(targetUrl, init);
  } catch {
    return NextResponse.json(
      { message: "Backend service is unavailable" },
      { status: 502 },
    );
  }

  let body: string;
  try {
    body = await response.text();
  } catch {
    return NextResponse.json(
      { message: "Backend response could not be read" },
      { status: 502 },
    );
  }

  const forwardHeaders = new Headers();
  const contentTypeHeader = response.headers.get("content-type");
  if (contentTypeHeader) {
    forwardHeaders.set("content-type", contentTypeHeader);
  } else {
    forwardHeaders.set("content-type", "application/json");
  }
  for (const header of ["x-page", "x-size", "x-total-elements", "x-total-pages"]) {
    const value = response.headers.get(header);
    if (value) {
      forwardHeaders.set(header, value);
    }
  }

  return new NextResponse(body, {
    status: response.status,
    headers: forwardHeaders,
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

export async function PUT(request: NextRequest, context: Params) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function DELETE(request: NextRequest, context: Params) {
  const { path } = await context.params;
  return proxy(request, path);
}
