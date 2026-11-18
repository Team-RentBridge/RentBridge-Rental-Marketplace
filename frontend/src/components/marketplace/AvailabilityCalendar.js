import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Ban, Check } from "lucide-react";
import API from "../../api/api";

const DAY_MS = 24 * 60 * 60 * 1000;

function toDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function AvailabilityCalendar({ equipmentId, startDate, endDate, onChange, isOwner }) {
  const today = useMemo(() => new Date(), []);
  const [month, setMonth] = useState(new Date(today.getFullYear(), today.getMonth(), 1));
  const [availability, setAvailability] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [blockStart, setBlockStart] = useState("");
  const [blockEnd, setBlockEnd] = useState("");
  const [blockReason, setBlockReason] = useState("");

  const rangeStart = toDateKey(today);
  const rangeEnd = toDateKey(new Date(today.getTime() + 180 * DAY_MS));
  const monthKey = `${month.getFullYear()}-${month.getMonth()}`;

  const loadAvailability = async () => {
    setLoading(true);
    try {
      const { data } = await API.get(`/equipment/${equipmentId}/availability`, {
        params: { from: rangeStart, to: rangeEnd },
      });
      if (!Array.isArray(data)) {
        throw new Error("Availability response was not a list");
      }
      setAvailability(Object.fromEntries(data.map((day) => {
        const date = String(day.date).slice(0, 10);
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
          throw new Error("Availability response contained an invalid date");
        }
        return [date, {
          ...day,
          owner_blocked: day.owner_blocked === true || day.owner_blocked === "true",
          available_quantity: Number(day.available_quantity),
        }];
      })));
      setError("");
    } catch (requestError) {
      setAvailability({});
      setError(requestError.response?.data?.error || "Could not load availability.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAvailability();
    // Load once per listing; local date keys are refreshed when the page is opened.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [equipmentId]);

  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1);
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells = [
    ...Array(firstDay.getDay()).fill(null),
    ...Array.from({ length: daysInMonth }, (_, index) =>
      new Date(month.getFullYear(), month.getMonth(), index + 1)),
  ];

  const selectDate = (date, isBlocked) => {
    const key = toDateKey(date);
    if (!startDate || endDate || key <= startDate) {
      if (isBlocked) return;
      onChange(key, "");
      setError("");
      return;
    }

    const first = new Date(`${startDate}T00:00:00`);
    const nights = Math.round((date - first) / DAY_MS);
    for (let offset = 0; offset < nights; offset += 1) {
      const occupied = availability[toDateKey(new Date(first.getTime() + offset * DAY_MS))];
      if (!occupied || occupied.owner_blocked || occupied.available_quantity < 1) {
        setError("That range includes a blocked or fully reserved night.");
        return;
      }
    }
    onChange(startDate, key);
    setError("");
  };

  const addBlock = async (event) => {
    event.preventDefault();
    setError("");
    try {
      await API.post(`/equipment/${equipmentId}/availability-blocks`, {
        start_date: blockStart,
        end_date: blockEnd,
        reason: blockReason,
      });
      setBlockStart("");
      setBlockEnd("");
      setBlockReason("");
      await loadAvailability();
    } catch (requestError) {
      setError(requestError.response?.data?.error || "Could not block those dates.");
    }
  };

  const removeBlock = async (blockId) => {
    try {
      await API.delete(`/equipment/${equipmentId}/availability-blocks/${blockId}`);
      await loadAvailability();
    } catch (requestError) {
      setError(requestError.response?.data?.error || "Could not remove blocked dates.");
    }
  };

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-5">
        <div className="mb-4 flex items-center justify-between">
          <button
            type="button"
            aria-label="Previous month"
            onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}
            disabled={monthKey === `${today.getFullYear()}-${today.getMonth()}`}
            className="rounded-lg p-2 text-white/60 hover:bg-white/10 disabled:opacity-20"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div className="text-sm font-black text-white">
            {month.toLocaleDateString(undefined, { month: "long", year: "numeric" })}
          </div>
          <button
            type="button"
            aria-label="Next month"
            onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}
            disabled={month >= new Date(today.getFullYear(), today.getMonth() + 6, 1)}
            className="rounded-lg p-2 text-white/60 hover:bg-white/10 disabled:opacity-20"
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        </div>

        <div className="mb-2 grid grid-cols-7 text-center text-[10px] font-black uppercase tracking-wider text-white/30">
          {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => (
            <div key={day} className="py-2">{day}</div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {cells.map((date, index) => {
            if (!date) return <div key={`empty-${index}`} />;
            const key = toDateKey(date);
            const entry = availability[key];
            const blocked = !entry || entry.owner_blocked || entry.available_quantity < 1;
            const isCheckoutDate = Boolean(startDate && !endDate && key > startDate);
            const disabled = key < rangeStart || (blocked && !isCheckoutDate);
            const selected = key === startDate || key === endDate;
            const inRange = startDate && endDate && key > startDate && key < endDate;
            return (
              <button
                type="button"
                key={key}
                disabled={disabled || loading}
                onClick={() => selectDate(date, blocked)}
                title={entry?.owner_blocked ? "Blocked by the owner" : `${entry?.available_quantity ?? 0} available`}
                className={`min-h-12 rounded-lg border px-1 py-1 text-center transition-colors disabled:cursor-not-allowed disabled:opacity-30 ${
                  selected ? "border-primary-400 bg-primary-600 text-white" :
                  inRange ? "border-primary-500/30 bg-primary-500/15 text-primary-200" :
                  blocked ? "border-white/5 bg-white/[0.02] text-white/25 line-through" :
                  "border-white/5 bg-white/[0.02] text-white/80 hover:border-primary-400/50 hover:bg-primary-500/10"
                }`}
              >
                <span className="block text-xs font-bold">{date.getDate()}</span>
                <span className="block text-[9px] opacity-60">
                  {entry?.owner_blocked ? <Ban className="mx-auto h-3 w-3" /> : entry?.available_quantity ?? ""}
                </span>
              </button>
            );
          })}
        </div>

        <div className="mt-4 flex flex-wrap gap-x-4 gap-y-2 text-[10px] font-bold text-white/40">
          <span className="flex items-center gap-1"><Check className="h-3 w-3 text-emerald-400" /> Available units</span>
          <span className="flex items-center gap-1"><Ban className="h-3 w-3 text-red-400" /> Blocked / unavailable</span>
        </div>
        {loading && <p className="mt-3 text-xs text-white/40">Loading availability…</p>}
      </div>

      {startDate && (
        <div className="rounded-xl border border-primary-500/20 bg-primary-500/5 p-4 text-sm text-white/70">
          <div>Pickup: <strong className="text-white">{startDate}</strong> at 9:00 AM</div>
          <div className="mt-1">Return: <strong className="text-white">{endDate || "Choose a return date"}</strong>{endDate ? " at 5:00 PM" : ""}</div>
          {endDate && (
            <div className="mt-1 text-xs text-white/40">
              Free cancellation until {new Date(new Date(`${startDate}T00:00:00`).getTime() - DAY_MS)
                .toLocaleDateString()} at 9:00 AM.
            </div>
          )}
        </div>
      )}

      {isOwner && (
        <form onSubmit={addBlock} className="space-y-3 rounded-xl border border-white/10 p-4">
          <h4 className="text-sm font-black text-white">Block dates for this listing</h4>
          <div className="grid grid-cols-2 gap-3">
            <input required type="date" min={rangeStart} value={blockStart}
              onChange={(event) => setBlockStart(event.target.value)}
              className="min-w-0 rounded-lg border border-white/10 bg-white/5 p-3 text-sm text-white" />
            <input required type="date" min={blockStart || rangeStart} value={blockEnd}
              onChange={(event) => setBlockEnd(event.target.value)}
              className="min-w-0 rounded-lg border border-white/10 bg-white/5 p-3 text-sm text-white" />
          </div>
          <input value={blockReason} onChange={(event) => setBlockReason(event.target.value)}
            placeholder="Reason (optional)"
            className="w-full rounded-lg border border-white/10 bg-white/5 p-3 text-sm text-white placeholder:text-white/30" />
          <button type="submit" className="rounded-lg bg-white/10 px-4 py-2 text-xs font-bold text-white hover:bg-white/20">
            Save blocked dates
          </button>
          {Object.values(availability)
            .filter((entry) => entry.owner_block_id)
            .filter((entry, index, entries) => entries.findIndex((value) => value.owner_block_id === entry.owner_block_id) === index)
            .map((entry) => (
              <button key={entry.owner_block_id} type="button" onClick={() => removeBlock(entry.owner_block_id)}
                className="ml-2 text-xs font-bold text-red-300 hover:text-red-200">
                Unblock {entry.date}
              </button>
            ))}
        </form>
      )}
      {error && (
        <div role="alert" className="flex items-center justify-between gap-3 text-sm font-semibold text-red-300">
          <span>{error}</span>
          <button type="button" onClick={loadAvailability} className="shrink-0 underline">
            Retry
          </button>
        </div>
      )}
    </div>
  );
}

export default AvailabilityCalendar;
