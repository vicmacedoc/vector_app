// Google Calendar import for the Routine add flow (GoogleCalendarClient): primary calendar, one local day.
import { fetch } from "@tauri-apps/plugin-http";
import { addDays } from "@/domain/dates";
import type { ActivityDraft } from "@/domain/daily/activity";
import { optionalValue } from "@/domain/daily/time";
import { accessToken } from "./auth";

const EVENTS = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

interface EventTime {
  date?: string;
  dateTime?: string;
}

interface CalendarEvent {
  id?: string;
  status?: string;
  summary?: string;
  description?: string;
  start?: EventTime;
  end?: EventTime;
}

const localMidnight = (date: string) => new Date(`${date}T00:00:00`);
const hhmm = (d: Date) => `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;

function friendlyError(status: number, text: string): string {
  let reason = "";
  let message = "";
  try {
    const err = JSON.parse(text)?.error;
    reason = err?.errors?.[0]?.reason ?? err?.details?.[0]?.reason ?? "";
    message = err?.message ?? "";
  } catch {
    /* not JSON */
  }
  if (reason === "SERVICE_DISABLED" || reason === "accessNotConfigured" || text.includes("calendar-json.googleapis.com")) {
    return "Google Calendar API is not enabled for this app's Google Cloud project. Enable “Google Calendar API” in Cloud Console (same OAuth project as Drive), wait a minute, then retry.";
  }
  if (status === 403 && (reason === "insufficientPermissions" || /insufficient|scope/i.test(message))) {
    return "Vector doesn't have Calendar access yet. Sign out and in again in Settings → Account to grant it.";
  }
  if (status === 403) return "Calendar access was denied (403). Enable Google Calendar API for your OAuth project, then retry.";
  return message || `Failed to load Google Calendar (${status})`;
}

/** Event start/end mapped onto HH:mm for [date]; all-day events become 09:00–10:00, multi-day events are clipped. */
function localTimes(ev: CalendarEvent, date: string): [string, string] | null {
  const start = ev.start;
  const end = ev.end ?? start;
  if (!start) return null;
  if (start.date && !start.dateTime) {
    const endExclusive = end?.date ?? addDays(start.date, 1);
    return date >= start.date && date < endExclusive ? ["09:00", "10:00"] : null;
  }
  if (!start.dateTime) return null;
  const s = new Date(start.dateTime);
  const e = end?.dateTime ? new Date(end.dateTime) : s;
  const dayStart = localMidnight(date);
  const dayEnd = localMidnight(addDays(date, 1));
  if (e <= dayStart || s >= dayEnd) return null;
  const clippedStart = s < dayStart ? dayStart : s;
  const clippedEnd = e >= dayEnd ? "23:59" : hhmm(e);
  return [hhmm(clippedStart), clippedEnd];
}

export async function calendarDayDrafts(date: string): Promise<ActivityDraft[]> {
  const params = new URLSearchParams({
    timeMin: localMidnight(date).toISOString(),
    timeMax: localMidnight(addDays(date, 1)).toISOString(),
    singleEvents: "true",
    orderBy: "startTime",
    maxResults: "250",
  });
  let res: Response | null = null;
  for (let attempt = 0; attempt < 2; attempt++) {
    res = await fetch(`${EVENTS}?${params}`, { headers: { Authorization: `Bearer ${await accessToken(attempt > 0)}` } });
    if (res.status !== 401) break;
  }
  if (!res || !res.ok) throw new Error(friendlyError(res?.status ?? 0, await res?.text().catch(() => "") ?? ""));
  const body = (await res.json()) as { items?: CalendarEvent[] };
  return (body.items ?? []).flatMap<ActivityDraft>((ev) => {
    if (ev.status?.toLowerCase() === "cancelled") return [];
    const times = localTimes(ev, date);
    if (!times) return [];
    return [{
      title: optionalValue(ev.summary) ?? "(No title)",
      startTime: times[0],
      endTime: times[1],
      labelName: null,
      type: "PLANNED",
      description: optionalValue(ev.description) ?? "",
      source: "GOOGLE_CALENDAR",
      externalId: ev.id ?? null,
    }];
  });
}
