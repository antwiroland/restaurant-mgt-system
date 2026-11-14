# Restaurant Management System: marketing website

The public product site for the **Restaurant Management System by KENVO Integrated Systems**. This is a
separate app. It doesn't import from, or change, `web/`, `mobile/` or `backend/`.

React 19 · TypeScript · Vite 6 · Tailwind CSS 4 · Framer Motion · Lucide icons. It's a single page, so there's no router.

```bash
cd marketing
npm install
npm run dev        # http://localhost:5180
npm run build      # type-check + production build into dist/
npm run preview    # serve dist/ on :5180
```

## Images

```bash
npm run images     # python scripts/prepare_images.py (needs Pillow)
```

- **Screenshots** come from `documentation/screen shots/`. They are only resized or cropped (for example, the
  table-QR pages are cropped to the phone-width column a guest sees), never edited.
- **Food photography** is downloaded from Unsplash ([Unsplash License](https://unsplash.com/license), free for
  commercial use). The photo IDs, with alt text, are listed in `FOOD` in the script, and downloads are cached in `.image-cache/` (git-ignored).
- The script writes WebP files in two widths to `public/images/`, the typed manifest `src/data/images.ts`, and the
  social share card `public/og-image.jpg`.

## Structure

```
src/
  components/   Navbar, Hero, Footer, FeatureCard, ScreenshotShowcase (tabbed tour), FoodGallery,
                DemoRequest, Contact, CTASection, Lightbox, Media (Photo, Shot, BrowserFrame, PhoneFrame), Logo, ui
  sections/     ValueProps, AboutProduct, CustomerOrdering, TableManagement, POSSection, PaymentSection,
                StaffManagement, AttendanceSection, PayrollSection, ReportingSection, WhoWeServe, WhyChoose
  pages/Home.tsx
  data/         site.ts (company, contacts, nav), images.ts (generated)
  styles/index.css (Tailwind theme: product palette + KENVO identity)
```

## Content rules

- Every product claim matches what the code does (see `FEATURES.md`). The **exit verification at the security
  post** (a temporary payment QR scanned on the way out) is not built yet. The Payments section shows it as a
  conceptual workflow, with each step labelled "Available today" or "Planned".
- Company details live only in `src/data/site.ts`, and are the ones KENVO supplied. Don't add contact details, social accounts,
  testimonials or figures that KENVO hasn't confirmed.

## Demo request form

There is no backend for leads yet, and the form says so on screen. On submit it validates the fields and then:

1. opens the visitor's email app with a pre-filled message to `info@kenvointegratedsystems.com` (cc the direct
   contact), or
2. sends the same summary by WhatsApp to 024 107 8835.

To capture leads automatically, replace the `mailto:` step in `DemoRequest.tsx` (`onSubmit`) with a `POST` to one of:

- a form service (Formspree, Basin, Web3Forms): create a form, then `fetch(endpoint, { method: "POST", body: JSON.stringify(f) })`;
- a small endpoint of your own, for example a serverless function that emails KENVO through SMTP or a transactional
  email API and stores the lead;
- a CRM's web-to-lead endpoint (HubSpot, Zoho).

Keep the success and error states honest: show success only after the server confirms.

## Deployment

`npm run build` produces a static `dist/` that any static host can serve (Netlify, Vercel, Cloudflare Pages,
Nginx). Before going live, set the canonical URL in `index.html` and make the `og:image` URL absolute
(`https://<domain>/og-image.jpg`), because some social networks need absolute image URLs.
