# Click&SaveAI — Product UX Source of Truth

Status: APPROVED / implementation authority

## Product promise
Click&SaveAI works proactively in the background to identify recurring household/service expenses, verify relevant alternatives, and surface concrete savings opportunities. It is not an expense tracker, budgeting app, or savings-goal planner.

## Non-negotiable product rules
1. Savings-first: the primary dashboard story is what Click&SaveAI found and what the user can save.
2. Truthfulness: never display invented savings. A monetary savings figure appears only when a real matched/verified offer supports it.
3. Referral, not switching: Click&SaveAI does not execute provider switching at this stage.
4. Provider handoff flow: opportunity -> current price vs offer -> savings -> user CTA -> prefilled details -> explicit approval -> send details to provider -> confirmation that provider will contact the user.
5. Success copy must state that details were sent to the provider and no service change was performed by Click&SaveAI.
6. Bills: users can view recognized bills and leave Click&SaveAI to pay through the provider's official payment destination. Click&SaveAI does not hold card details or process bill payment.
7. Remove budgeting/expense-manager concepts from primary UX: no monthly savings goal, no 'financial situation' framing, no 'where your money goes' as the dashboard hero, and no manual expense entry as a primary action.

## Approved visual direction
- Light Premium only for primary product screens; no dark full-screen product experience.
- White / very light cool backgrounds with generous whitespace.
- Navy/dark ink for trust and primary text.
- Blue for primary actions and navigation selection.
- Green is reserved primarily for verified savings, positive financial value, and confirmed success.
- Avoid purple/AI-generic visual language.
- Rounded premium cards, consistent spacing, restrained shadows/borders.
- RTL Hebrew is first-class.
- Respect Android status/navigation safe areas; app content must never collide with system bars.

## Brand
Approved brand direction includes the Click&Save hand/finger press symbol. The symbol communicates Click -> Save and should remain simple enough to work as an app icon. Do not use a robot mascot.

## Primary navigation
1. Home — savings-first dashboard.
2. Bills — recognized bills and provider payment exits.
3. Savings — verified opportunities and opportunities under review.
4. Me — profile, connections, preferences, privacy.

## Canonical user journey
### First run
Opening -> trust/privacy explanation -> Google sign-in -> Gmail read-only consent -> animated scan/progress -> first results.

### Dashboard
Hero prioritizes verified annual/monthly savings when available. Secondary metrics may show services monitored and recognized recurring spend, but spend must not dominate the product story. Show top actionable savings opportunities prominently.

When no verified savings exists, use a calm review state such as 'אנחנו עדיין בודקים עבורך' and explain what is being checked. Do not replace missing savings with an unrelated spend number.

### Opportunity detail
Show:
- service/provider/category
- current monthly price
- matched offer monthly price
- verified monthly saving in green
- verified annual saving in green
- relevant offer terms / why it matches
- primary CTA: 'אני רוצה לחסוך ₪X בחודש' when X is verified

### Provider referral
1. Show prefilled user details.
2. Explain exactly what is shared and with whom.
3. User explicitly taps 'שלחו את הפרטים לספק'.
4. Show a lightweight sending animation/state.
5. Confirmation: 'הפרטים שלך הועברו לספק'.
6. Supporting copy: provider representative will contact the user to complete the transaction.
7. Explicit trust note: Click&SaveAI did not change the user's existing service.

### Bills
Each bill should prioritize provider, category, amount, period/due information, and status. When an official provider payment URL is available, CTA is 'לתשלום אצל הספק'. Never imply payment occurs inside Click&SaveAI.

## Motion
Use subtle purposeful animation for scanning, verification, provider handoff and success. Motion should communicate progress and trust, not entertainment. Never fake progress or claim a check completed before backend evidence exists.

## Copy principles
Consumer language only. Avoid internal terms such as lead, CRM, parser, Gmail message source, dispatch envelope, or backend status. Explain outcomes: 'מצאנו', 'בדקנו', 'הפרטים הועברו לספק', 'ממתינים להצעה מאומתת'.

## Implementation gate
A new Android APK is not product-approved unless:
- system-bar/header collision is fixed;
- primary screens follow this document;
- savings figures obey verified-data truthfulness rules;
- referral flow does not imply Click&SaveAI performs the switch;
- Bills payment exits to provider rather than in-app payment;
- primary UI is Light Premium;
- monthly savings goal / expense-tracker-first framing is absent from primary flow.

This document overrides older UI concepts that conflict with these rules.