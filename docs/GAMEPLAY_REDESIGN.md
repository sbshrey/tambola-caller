# Gameplay redesign: research, decisions, and release gates

26 September 2026 · branch `shrey/full-tambola-game` · supersedes the alpha12 screen layout.

## Product brief

Fast, free social Tambola: offline solo with labelled computer opponents, family play on one device, and private online rooms. One prominent Quick play action, common prizes, five-second calling by default, assisted marking, and no compulsory account for solo. Only the current player's tickets appear during play. One to six tickets must fit together without scrolling or a ticket carousel. A six-ticket strip contains every number 1–90 exactly once; smaller hands take a subset of a shuffled strip. Different players can share numbers, as in physical Tambola. Existing saved rounds retain their original tickets.

## Research and evidence boundaries

Public Play Store listing/review samples and marketing screenshots were inspected on 26 September 2026. This is a targeted qualitative sample, not a representative review analysis or an installed competitor benchmark. Ratings vary by region/device and are not used to rank competitors. Review allegations are user reports, not verified defects. Designs are inspiration only; no third-party art, branding, or layouts are imported.

| Source | Observation | Decision for our game |
| --- | --- | --- |
| [Online Tambola Friends Housie, nLife](https://play.google.com/store/apps/details?id=com.herokuapp.tambolah&hl=en) | 30 Jan 2026 review describes confusing mid-game rule additions and notifications covering the caller. Older 2020 reviews praise simple controls and adjustable pace. Developer replies say some old missing chat/caller features were added. Published prize-selection screen has many choices. | Freeze rules before dealing. Default to familiar prizes. Keep rule editing outside live play; reserve space for win feedback so the caller/tickets never shift. |
| [Octro Tambola](https://play.google.com/store/apps/details?id=com.octro.tambola&hl=en_GB) | Live browser sample: 15 Sep 2026 review dislikes purchase pressure; 13 Sep questions computer fairness. An older 5 Apr 2020 review reports reconnect interruptions. Published landscape screenshot combines recent calls, player avatars, ticket grids and claim controls. These reviews do not prove current cheating or connection defects. | No coins/pay-to-continue. Explicit computer labels and the same draw/award rules for everyone. Stable cached tickets and clear reconnect state. Show score/award evidence without exposing opponents' tickets. |
| [Housie Tambola Number Caller90](https://play.google.com/store/apps/details?id=com.unboxknowledge.housie&hl=en_US) | 4 Aug 2026 review values offline play; 5 Sep praises family gatherings. Dec 2024 review disputes computer awards; developer's June 2026 reply says the system was revised. | Offline remains first-class. Explain ties and automatic verification before play; never claim the historical issue persists. |
| [Tambola Fun Number Calling App](https://play.google.com/store/apps/details?id=com.titaapps.tambolafun&hl=en) | Caller-oriented product with several languages and pace choices. 2019 review praises ease/colors; 2020 ticket-site complaint was disputed by developer. It is not a complete digital multiplayer comparison. | Ticket generation, number voice and effects stay inside our app and work offline. Avoid external ticket-download flows. |
| [Housie Classic: Online Tambola](https://play.google.com/store/apps/details?id=com.applaytechHousie&hl=en) | Listing advertises private rooms, digital tickets, auto-marking, familiar patterns and bilingual calling. No useful review sample was exposed in the fetched listing. | These are baseline features, not differentiation. Compete on fast entry, legible complete hands and dependable recovery. |
| [Digital Housie concept, Nuance Infotech / Dribbble](https://dribbble.com/shots/26984529-Digital-Housie-Real-Time-Social-Game-App-UI-UX) | Visually inspected onboarding, lobby and gameplay sheets. Teal palette and stacked paper grids establish a coherent game identity. The concept also uses OTP screens, dense prize information, and board/ticket switching. It is a concept, not usability evidence. | Use a coherent original table-and-paper language, but immediate solo entry and simultaneous owned-ticket visibility. No mandatory phone/OTP setup. |

Figma Community was inaccessible through the search tool (robots restriction); no Figma community file was evaluated or copied. Dribbble and the actual Play screenshots provide sufficient visual references to proceed.

Also inspected [SPEC INDIA's Tambola/Bingo design post](https://dribbble.com/shots/6726698-Tambola-Bingo-Game-Design-and-Development): its purple/amber marquee-style brand artwork conveys game energy, but the visible post does not establish actual ticket usability. Our direction uses a quieter surface behind numbers, reserving bright accents for the call and wins.

## Audit of our alpha12

1. Home places promotional prose, tutorial prompts and artwork before play controls.
2. Solo starts with names, avatars, opponents, ticket count and many prize settings on a long page.
3. Live play sits in a vertically scrolling page and exposes only one selected ticket.
4. Offline computer tickets are selectable by the human player.
5. Manual marking requires opening a separate ticket dialog; six cards amplify navigation cost.
6. Repeated caller/status text and large stacked controls compete with the number itself.
7. Ten-second default and manual start do not satisfy quick play.
8. Dealer prevents identical layouts but does not prevent shared numbers within a hand.
9. Online account/history/privacy controls occupy the live round page.
10. Celebrations can expand the layout and move the player's tickets.

## Original direction and implementation order

**A. Correct hands.** Deal bounded, randomized six-ticket strips; sample 1–6. Verify every ticket's 3×9 / 15 numbers / 5 per row / correct column ranges / ascending columns, disjoint hands, room limits, seeds, rematches and old saves. All players use the same dealer; no predictive bot advantage.

**B. Play immediately.** Quick play uses three tickets, two labelled computer opponents, assisted marking, five-second calls and Early Five / Corners / three lines / Full House. Start after a short first-call interval. Resume existing rounds instead of silently discarding them. Custom and family setup remain available; advanced rules are optional.

**C. Stable game table.** Deep green table, warm paper grids, mint dabs, amber latest-number accent. Fixed compact header, large call, recent calls, visual prize rail, complete owned hand, and large bottom actions. No ticket carousel and no scrolling in the live table. Portrait stacks cards; wide windows can use two columns. Manual mode has one large action to dab confirmed called numbers. Rows retain screen-reader descriptions; cells are not undersized tap targets. Family hands use an explicit paused handoff rather than exposing every player's cards at once.

**D. Feedback and online flow.** Short number reveal and dab feedback; fixed-height win announcement, reduced-motion equivalent, no full-screen win interruption. Automatic verified awards remain authoritative. Distinguish live/reconnecting/paused; never fabricate numbers while disconnected. Rules, scores, call history, and room management are deliberate secondary views.

**E. Iterate on the actual APK.** Inspect 1/3/6 tickets at 360×640 and 411×731 logical sizes, landscape, Hindi, 200% text, light/dark, and reduced motion. Test quick start, manual dabs, ownership, handoff, pause/resume, finish/rematch, process death and reconnect. Fix clipped elements and confusing interactions from the captured screens before packaging a candidate.

## Measurable acceptance, not a market-superiority claim

- Fresh offline Quick play: one action from home; no name, account, tutorial or prize form required.
- All 1–6 owned tickets simultaneously visible; no opponent grids, no forced ticket switching.
- All standard prize choices fixed before dealing; accessible details available.
- No duplicate numbers within a newly dealt hand. Six tickets cover exactly 1–90.
- Five-second default calling; recordings do not overlap and pausing is immediate. User-selected slower preferences remain respected in custom play.
- Number/ticket geometry does not move on awards, reconnect notices or count changes.
- Interactive controls at least 48dp; compact ticket cells are display-only, with a large manual dab control and semantic row summaries.
- No clipping at the agreed phone sizes; explicitly document readability constraints at extreme split-screen sizes rather than claiming universal legibility.
- Measure first draw, local draw-to-frame, frame jank, round network bytes, reconnect recovery, and automatic call delivery on the final candidate. Local server measurements are not real mobile-network latency.
- The earlier nine-round/320-client soak stopped after five completed rounds without a terminal result. It is partial pre-redesign evidence and cannot validate the new dealer, new UI or a hosted mobile network.
- A release additionally needs production signing, hosted TLS service, verified links, backups/monitoring, privacy/support identity, appropriate store declarations and actual device coverage. None are satisfied by a visual mockup or debug APK.

## Iterations from native APK review

1. The first six-ticket screenshot showed an ambiguous Early Five symbol. Replaced it with a clear numeral 5. Unclipped-bound checks confirmed all bottom controls fit.
2. A 360×640 screen at 200% text exposed tiny ticket numbers and clipped ticket ordinals despite passing screen-bound assertions. Moved the compact prize rail beside the caller, removed the idle caption in compact layouts, reduced internal paper padding, and used labelled playback icons at large text sizes. The native layout check now also requires number boxes at least 12dp high and action targets at least 48dp.
3. Landscape uses three columns when the hand area is short. The owner moves into the header, leaving two full rows for six cards. All cards remain visible together.
4. Screenshot captures initially missed fixed labels after theme changes. Advancing the Compose test clock before capture resolved this test artifact; no speculative theme workaround was added to the app.
5. Collapsed lobby rule explanations, made the tutorial teach the same inline Dab action, removed obsolete ticket-edit dialogs/expanding win cards, reused ticket plurals, and kept online retry/pause controls at fixed height.
6. Native coverage now checks actual live-table motion with both system animation settings: calls appear immediately, the animation finishes, reduced motion stays still, and verified awards do not move the ticket hand. Tiny effects remain inside the caller area.

Current checks and the remaining release gates are recorded in [FULL_GAME_PROGRESS.md](FULL_GAME_PROGRESS.md). Emulator geometry and automated checks do not establish actual TalkBack usability, physical-device performance, native-speaker copy quality, or market superiority.

7. The first independent two-native-client run exposed a Ready collision: another member becoming ready caused a stale-revision dialog. Added persisted agreement context and at most three automatic rebases after definite rejection, only while rules, roster, avatars, host and room lock remain unchanged. Unknown network outcomes retain their original identity. A forced native collision passes; changing rules instead keeps the player unready and requests review.
