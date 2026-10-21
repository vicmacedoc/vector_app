import { native, errorMessage, type AccessToken } from "@/platform/native";

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? "";
const CLIENT_SECRET = import.meta.env.VITE_GOOGLE_CLIENT_SECRET ?? "";

export class SignedOutError extends Error {
  constructor() {
    super("Sign in to Google Drive");
  }
}

let cached: AccessToken | null = null;

export const hasClientId = () => CLIENT_ID.trim().length > 0;

export async function signIn(): Promise<void> {
  cached = await native.signIn(CLIENT_ID, CLIENT_SECRET);
}

export async function signOut(): Promise<void> {
  cached = null;
  await native.signOut();
}

export const isSignedIn = () => native.signedIn();

/** Valid access token; the Rust side refreshes it from the stored refresh token. */
export async function accessToken(force = false): Promise<string> {
  if (!force && cached && cached.expiresAtMs - 60_000 > Date.now()) return cached.accessToken;
  try {
    cached = await native.accessToken(CLIENT_ID, CLIENT_SECRET, force);
    return cached.accessToken;
  } catch (e) {
    cached = null;
    if (errorMessage(e).includes("signed_out")) throw new SignedOutError();
    throw e;
  }
}
