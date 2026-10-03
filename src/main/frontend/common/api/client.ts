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

export async function getJson<T>(url: string, params: Params): Promise<T> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    query.append(key, String(value));
  }
  const response = await fetch(`${url}?${query}`, {
    headers: { Accept: "application/json" },
  });
  await ensureOk(response);
  return (await response.json()) as T;
}

async function postFormRaw(
  url: string,
  params: Params,
  signal?: AbortSignal,
): Promise<Response> {
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
    signal,
  });
  return ensureOk(response);
}

export async function postForm(url: string, params: Params): Promise<void> {
  await postFormRaw(url, params);
}

/** POST that returns a JSON body, for read endpoints that require POST. */
export async function postFormJson<T>(
  url: string,
  params: Params,
  signal?: AbortSignal,
): Promise<T> {
  const response = await postFormRaw(url, params, signal);
  return (await response.json()) as T;
}
