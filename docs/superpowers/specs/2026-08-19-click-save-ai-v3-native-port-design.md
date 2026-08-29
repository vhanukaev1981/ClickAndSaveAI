# Click & Save AI V3 Native Port Design

## Status

Approved architectural direction: **Lovable is the product/design source for V3; the production Android app remains native Jetpack Compose.**

This design exists to prevent the web/Lovable implementation from becoming a second production runtime or introducing a parallel authentication/backend stack.

## Goal

Ship the final Click & Save AI V3 consumer experience to Google Play as a **native Android AAB** that visually and behaviorally matches the approved Lovable V3 product experience while preserving the already verified production backend, identity, security, signing, and financial-truth contracts.

## Current Production Baseline

- Protected source baseline: `887518646fb66b36b10345fe2187e087457395ae`.
- Existing Android UI runtime: Jetpack Compose.
- Existing primary destinations: Home/Dashboard, Invoices, Savings/Providers, Activity, Profile.
- Existing AI screen/functionality already exists in native code and must be promoted into primary product navigation rather than reimplemented as a web-only feature.
- Existing production flows include Firebase Auth, Google Sign-In, Gmail readonly authorization, backend token exchange, Firebase Functions, Firestore, App Check / Play Integrity, push notifications, financial refresh, savings opportunity state, provider lead state, savings realization evidence, disconnect/data-delete/account-delete flows.

## Architecture Decision

### Production runtime

The Google Play application remains **native Android + Jetpack Compose**.

The following are explicitly out of scope for the production runtime:

- embedding the Lovable web app in a WebView;
- Capacitor/Cordova migration;
- introducing a second JavaScript OAuth stack;
- introducing Lovable Gmail connectors or `GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY`;
- replacing existing Firebase/Google auth flows with browser/web implementations.

### Lovable's role

Lovable is the **V3 product-design reference implementation**. It may contain React/TypeScript routes/components for preview and design iteration, but those files are not treated as the production Android runtime.

The native port must reproduce the approved Lovable information architecture, visual hierarchy, interaction patterns, microcopy, state treatments, and motion intent using Compose and the existing Android state/data layer.

## Product Experience Contract

The final app must behave as a personal savings operating system rather than a generic financial dashboard.

Primary user loop:

`CONNECT → DISCOVER → UNDERSTAND → ACT → SAVE → SEE PROGRESS`

The user should immediately understand:

1. how much has been **realized/verified** as saved;
2. how much **potential** savings remain;
3. what the **single best next action** is.

## Financial Truth Contract

This contract is non-negotiable across every redesigned screen.

- Realized savings and potential savings must never be visually conflated.
- Unknown data must never be rendered as zero.
- Potential savings must be labeled as potential.
- Commercial facts, prices, savings, discounts, provider offers, and AI claims must remain sourced from authoritative backend truth.
- Partial data may display already verified information while clearly marking still-loading/unknown fields.
- Error states must not erase previously verified financial truth.

## Final Primary Navigation

V3 primary navigation:

1. `בית`
2. `חיסכון`
3. `AI`
4. `פעילות`
5. `אני`

Existing invoices and provider functionality remain reachable but are no longer first-class bottom-navigation entities.

### Destination mapping

- **Home**: command center, monitoring state, realized/potential savings, next-best action, financial snapshot, recent discoveries, entry points to invoices.
- **Savings**: opportunity workspace, provider/offer detail, opportunity lifecycle, realized savings history/action outcomes.
- **AI**: native savings assistant using existing AI/backend state and truth rules.
- **Activity**: chronological human-readable product activity, with technical detail secondary.
- **Me**: identity, connections, preferences, privacy/data controls, app information.

## Screen Design

### Home / Financial Command Center

Required hierarchy:

1. compact greeting / orientation;
2. savings hero separating:
   - `חיסכון שמומש`
   - `פוטנציאל לחיסכון`;
3. one `הדבר הכי משתלם לעשות עכשיו` recommendation;
4. compact monitoring/sync status;
5. compact financial snapshot;
6. recent discoveries/activity feed;
7. contextual entry to all invoices/bills.

The existing permanent technical/background-work banner should become a compact status element rather than dominating the top of every screen.

### Savings

Savings is the primary action workspace.

Opportunity presentation should map existing truthful states into a clear human lifecycle where supported:

`נמצא → נבדק → מוכן לפעולה → בתהליך → מומש`

No lifecycle stage may be fabricated if the backend does not support it.

Each opportunity must answer:

- provider/service;
- current situation;
- why this is an opportunity;
- potential monthly/annual difference if authoritative;
- freshness/source/verification context;
- one clear next action backed by existing functionality.

Provider details remain part of this journey rather than a disconnected database-like section.

### AI

The existing native AI capability becomes a primary destination named `AI` with the consumer-facing title `עוזר החיסכון שלך`.

The native implementation must preserve the existing backend and non-hallucination contract.

The UI should include:

- smart suggestion chips;
- context-aware prompts only when real state exists;
- concise user/assistant message presentation;
- uncertainty and source/verification cues for financial claims;
- no generic ChatGPT-clone visual treatment.

### Activity

Activity becomes a grouped human timeline (`היום`, `אתמול`, `השבוע`) showing meaningful events such as sync, invoice discovery, new savings opportunity, savings action, and realized savings.

Raw technical logs are not the primary presentation.

### Me / Profile

Sections:

- identity and connection status;
- Google/Gmail connection;
- notification status;
- savings/notification preferences supported by current data;
- privacy and data controls;
- disconnect Gmail;
- delete imported data;
- delete account;
- help/legal/version.

Destructive actions remain separated and confirmed.

### Invoices

Invoices remain native and reachable from Home and contextual savings flows.

Presentation should be a modern financial list with provider, category, amount, date/period, and truthful status. Filters are allowed only for real fields.

## Onboarding and First-Sync Experience

The redesigned onboarding may have up to three meaningful pre-authentication stages:

1. value proposition;
2. how Click & Save AI helps;
3. privacy-first Google/Gmail explanation.

The real Google/Firebase auth and Gmail readonly authorization flows remain unchanged.

First-sync UI should present calm staged progress when real granular states are available; otherwise use a neutral overall scanning state. It must not fabricate completed stages.

The first-results reveal should surface authoritative counts and potential savings only when real data is available.

## Native Auth and Gmail Contract

The only production Gmail path remains the existing native Android flow.

Native callbacks/logic include the existing `onRequestGmailAuthorization` path and the current Google AuthorizationClient + `gmail.readonly` implementation.

The V3 port must not require or add:

- `startGmailConnect`;
- `GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY`;
- Lovable Gmail connector clients;
- new OAuth scopes;
- frontend Gmail secrets.

Lovable preview-only unavailable states may exist in the design source, but the native production app must invoke existing Android authentication/authorization logic.

## Design System

### Brand palette

Retain the existing brand family:

- Navy / text anchor: `#0F172A`
- Primary action blue: `#2563EB`
- Savings/success emerald: `#00C896`
- AI-specific restrained blue→violet accent only where contextually useful
- Warm amber for attention
- Soft red for errors

### Visual character

Target: premium 2026 fintech/AI consumer product.

Use:

- generous whitespace;
- clear money-number hierarchy;
- 20–28dp-equivalent rounded surfaces where appropriate;
- subtle surface contrast/shadow rather than heavy borders;
- restrained gradients;
- one dominant CTA per visual section;
- concise Hebrew microcopy;
- native RTL layout rather than mechanical mirroring.

Avoid:

- enterprise dashboard density;
- endless identical cards;
- neon/crypto aesthetics;
- excessive glassmorphism;
- large persistent banners;
- fake charts/data;
- overly technical user-facing copy.

## Motion and Feedback

Motion communicates state, not decoration.

Allowed patterns:

- 180–350ms transitions;
- tab/icon transitions;
- skeleton→content;
- savings number count-up;
- subtle realized-savings confirmation;
- AI thinking indication;
- bottom-sheet spring motion;
- restrained haptics for success, key confirmation, and tab selection where platform appropriate.

## Reusable Native Component Targets

Prefer focused Compose components such as:

- `SavingsHero`
- `RealizedSavingsMetric`
- `PotentialSavingsMetric`
- `NextBestActionCard`
- `MonitoringStatus`
- `FinancialSnapshot`
- `OpportunityCard`
- `OpportunityStatus`
- `AIInsightCard`
- `ActivityTimelineItem`
- `VerificationBadge`
- `SourceFreshness`
- `ConnectionStatus`
- `EmptyState`
- `LoadingState`
- `SectionHeader`

Exact names may follow existing project conventions. Do not create abstraction solely for abstraction's sake.

## State Coverage

Every primary destination must have polished native handling for:

- unauthenticated;
- disconnected;
- loading/checking;
- ready;
- partial;
- failed;
- empty;
- action in progress;
- action success where applicable.

The UI must preserve previously verified truth during partial/failure conditions.

## Accessibility and Device Support

- Strong color contrast.
- Comfortable tap targets.
- Semantic content descriptions.
- Critical meaning not conveyed by color alone.
- Resilient RTL mixed Hebrew/number/₪ formatting.
- Layouts must work on compact, standard, and large Android phones.
- Respect status/navigation insets and edge-to-edge behavior.

## Porting Workflow

### Phase A — Finish Lovable V3 reference

- Complete all approved V3 primary screens and states in Lovable.
- Treat Lovable as product/visual reference only.
- Do not add new production backend/auth dependencies to make preview features work.
- Capture final screen states, copy, hierarchy, component behavior, and motion intent.

### Phase B — Native mapping

- Map each Lovable screen/component to the existing Compose destination/state.
- Identify existing business logic and callbacks each V3 CTA must reuse.
- Create a screen-by-screen native port matrix before changing Android UI.

### Phase C — Native implementation

- Port design system and reusable components first.
- Port navigation/information architecture.
- Port Home, Savings, AI, Activity, Me, and contextual Invoices/provider details.
- Preserve stable test tags where practical and update UI tests only when navigation semantics change.

### Phase D — Verification

- Build and run existing tests.
- Verify auth/Gmail/App Check/push/backend contracts are unchanged.
- Verify no new WebView/web OAuth runtime dependency exists.
- Verify truth semantics across ready/partial/error/empty states.
- Verify native RTL and compact-device layouts.

### Phase E — Release candidate

Only after the native port passes review and CI:

- create a new signed Production candidate;
- compute and record new artifact/AAB SHA-256;
- verify package/signing/version identity;
- resume Block 3I using the new approved AAB;
- do not upload the older pre-V3 AAB.

## Out of Scope

- Backend feature redesign.
- Firebase/Firestore/Functions contract changes unless a native port proves absolutely impossible without one; any such need requires a new explicit approval gate.
- OAuth scope expansion.
- App Check enforcement changes.
- signing key rotation.
- production/open testing rollout.
- fake provider switching, checkout, transactions, or savings.

## Acceptance Criteria

The V3 native port is ready for a new release candidate only when all of the following are true:

1. Primary native navigation is `בית | חיסכון | AI | פעילות | אני`.
2. Home clearly and separately renders realized vs potential savings.
3. Home exposes one truthful next-best action when available.
4. Savings opportunities preserve authoritative state and never overstate realized value.
5. Native AI remains backend-grounded and integrated into the product hierarchy.
6. Invoices/provider capabilities remain reachable.
7. Existing native Google/Firebase/Gmail flow remains the only production auth path.
8. No Lovable connector secret or WebView runtime dependency is introduced.
9. Push, App Check, Firebase Functions, Firestore, delete/disconnect flows remain intact.
10. Loading/partial/error/empty states preserve financial truth.
11. RTL, accessibility, and compact-phone layout are validated.
12. Android build/tests/CI pass.
13. A new signed AAB is produced from the approved V3 native source and independently fingerprinted before Play upload.

## Release Governance

The pre-V3 AAB and artifact remain historical evidence only and must not be uploaded once V3 native porting begins.

Block 3H remains closed unless the V3 work actually changes backend/infrastructure contracts. Pure Android UI/UX/native presentation changes do not reopen it.

Block 3I remains the active release gate but its candidate lock must be refreshed after the V3 native port is approved and rebuilt.
