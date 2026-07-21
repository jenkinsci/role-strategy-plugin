export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly body: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type Params = Record<string, string | number | boolean | undefined | null>;

async function ensureOk(response: Response): Promise<Response> {
  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(
      `${response.status} ${response.statusText}`,
      response.status,
      body,
    );
  }
  return response;
}

export async function postForm(url: string, params: Params): Promise<void> {
  const body = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    body.append(key, String(value));
  }
  const headers = crumb.wrap({
    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
    Accept: "application/json, text/plain, */*",
  });
  const response = await fetch(url, {
    method: "POST",
    headers,
    body,
  });
  await ensureOk(response);
}
