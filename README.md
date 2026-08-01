# cloud-itonami-isic-1410

Open Business Blueprint for **ISIC 1410**: manufacture of wearing
apparel (except fur) — the downstream *clothing* (衣) vertical of the
衣食住 scaffold batch (ADR-2607122200), paired with
[cloud-itonami-isic-1311](https://github.com/cloud-itonami/cloud-itonami-isic-1311)
(textile spinning) upstream.

**Maturity: `:implemented`** — `src/apparel/` implements the
`ApparelOperationActor` as a `langgraph.graph` StateGraph
(`apparel.actor/build`) wired to the contained advisor
(`apparel.advisor`, injected behind a protocol) and the independent
`apparel.governor`:
`:intake -> :advise -> :govern -> :decide -+-> :commit (clean)
+-> :request-approval (interrupt-before, human-in-the-loop)
+-> :hold (hard violation)`.

不変条件は 1 つ: **governor が拒否した書き込みは決して起きない**。
SSoT を書くノードは `:commit` の 1 箇所だけで、止めた事実も台帳に積む
（残さないと『提案されなかった』と『提案されたが止まった』の区別がつかない）。
phase gate が緩められるのは『自動で確定してよいか』だけで『通してよいか』
ではなく、HARD 違反は phase では覆せない。

39 tests / 149 assertions green（`clojure -M:dev:test`）。 ISIC division 13-14 (textiles/apparel) sits in **rollout
Wave 3 (production/robotics)** of the reverse-toposort plan
(ADR-2607121000): implementation is gated on the robotics premise
(ADR-2607011000). Publishing the blueprint now is deliberate
ammunition loading for when that gate opens (ADR-2607122100 Track A).

## What the implemented actor will be

**ApparelOps-LLM ⊣ Apparel Governor** — the fleet-standard pattern:
the advisor LLM drafts order intake, pattern/size-spec management,
cut-plan and QC scheduling, and per-lot supply-chain provenance
(fair-labor transparency is a first-class social impact here); the
independent `:apparel-governor` (a keyword unique fleet-wide) gates
every action; physical-domain work (cutting, sewing, pressing,
packing) is executed by robots under `kotoba-lang/robotics` safety
classes, never dispatched directly by the LLM.

Operating states: `intake → design → produce → inspect → package → audit`.

## Why open

AGPL-3.0-or-later, forkable by any qualified operator, so local
garment makers never surrender production and provenance data to a
closed SaaS. Part of the [cloud-itonami](https://itonami.cloud) open
business fleet.
