import { ExternalLink, MapPin } from "lucide-react";

function DeliveryMapCard({ booking }) {
  const search = [booking.delivery_address, booking.delivery_area]
    .filter(Boolean)
    .join(", ");
  const encodedSearch = encodeURIComponent(search);
  const mapUrl = `https://maps.google.com/maps?q=${encodedSearch}&z=14&output=embed`;
  const directionsUrl = `https://www.google.com/maps/search/?api=1&query=${encodedSearch}`;

  return (
    <article className="overflow-hidden rounded-3xl border border-white/10 bg-white/[0.03]">
      <div className="flex items-start justify-between gap-4 p-6">
        <div className="min-w-0">
          <h3 className="truncate font-black text-white">{booking.title}</h3>
          <p className="mt-1 flex items-start gap-2 text-sm text-white/50">
            <MapPin className="mt-0.5 h-4 w-4 flex-shrink-0 text-primary-400" />
            <span>{search}</span>
          </p>
        </div>
        <span className="flex-shrink-0 rounded-full border border-primary-500/30 bg-primary-500/10 px-3 py-1 text-[10px] font-black uppercase tracking-wider text-primary-300">
          {(booking.delivery_status || "scheduled").replaceAll("_", " ")}
        </span>
      </div>

      <iframe
        title={`Delivery destination map for ${booking.title}`}
        src={mapUrl}
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        className="h-64 w-full border-0 bg-white/5"
      />

      <div className="flex flex-wrap items-center justify-between gap-3 p-5">
        <div className="text-xs text-white/40">
          {booking.start_date
            ? `Scheduled ${new Date(booking.start_date).toLocaleDateString()}`
            : "Delivery scheduled"}
          {booking.pickup_window ? ` · ${booking.pickup_window}` : ""}
        </div>
        <a
          href={directionsUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-2 rounded-lg bg-white/10 px-3 py-2 text-xs font-bold text-white transition-colors hover:bg-white/20"
        >
          Open in Maps
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      </div>
      <p className="px-5 pb-4 text-[10px] text-white/30">
        Map search is provided by Google Maps. This is the delivery address, not live vehicle tracking.
      </p>
    </article>
  );
}

export default DeliveryMapCard;
