# cloud-itonami-isic-1410: Manufacture of wearing apparel

Open Business Blueprint for **ISIC Rev.5 1410**: manufacture of wearing
apparel (except fur) — an autonomous "actor" (LLM advisor behind an
independent Governor, langgraph-clj StateGraph, append-only audit
ledger) that coordinates back-office apparel-plant **operations**:
order intake, pattern/size-spec *metadata* (not a craft engine this
wave), quality/labeling concern flagging, and outbound shipment
coordination.

This repository designs a forkable OSS business for community apparel
manufacturing — fair-labor transparency is a first-class social impact
— run by a qualified operator so a garment plant keeps its own
operating records instead of renting a closed SaaS. Paired with
[cloud-itonami-isic-1311](https://github.com/cloud-itonami/cloud-itonami-isic-1311)
(textile spinning) upstream.

## What this actor does

Proposes **plant operations coordination**, not machine operation:
- `:order-intake` — production-order intake / administrative record logging
- `:pattern-spec` — pattern/size-spec *metadata* update (no craft engine this wave)
- `:quality-flag` — surface a quality/labeling concern (always escalates)
- `:shipment-coordinate` — outbound garment shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY** (sewing lines, cutters, presses; safety certification):

- Does NOT control sewing, cutting, or pressing equipment directly
- Does NOT finalize a safety certificate (`:safety-cert-finalized?` permanently blocked)
- Does NOT run a pattern craft engine (geometry / grading / CAD) — later wave
- Does NOT make plant-safety or labor-safety decisions (plant supervisor exclusive)
- ONLY proposes/coordinates operations back-office; high-stakes actuation requires explicit human approval
- Quality-flag always escalates — never auto-decided

## Architecture

Classic governed-actor pattern (`apparel.operation/build`, a langgraph-clj StateGraph):
1. **`apparel.advisor`** (sealed intelligence node, `ApparelAdvisor`): proposes decisions only, never commits
2. **`apparel.governor`** (independent, `Apparel Governor` / `:apparel-governor`): validates against domain rules, re-derived from `apparel.registry`'s pure functions and `apparel.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Request `:effect` must be `:propose`
     - `:op` must be in the closed four-op allowlist
     - Proposal `:effect` must be one of the four propose-shaped effects
     - Direct sewing/cutting/pressing control (`:direct-operate? true`) is PERMANENT, unconditional block
     - Finalizing a safety cert (`:safety-cert-finalized? true`) is PERMANENT, unconditional block
     - Order must be independently verified/registered before shipment coordination
     - A shipment may not push an order's own recorded shipped quantity past its own logged quantity
     - No fabricated `:size-code` on a pattern-spec patch
   - ESCALATE (always human sign-off, overridable by a human):
     - `:quality-flag` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`apparel.phase`** (Phase 0->3 rollout): `:pattern-spec`/`:quality-flag`/`:shipment-coordinate` are NEVER in any phase's `:auto` set; only `:order-intake` may auto-commit at phase 3 when clean
4. **`apparel.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol

Also ships `apparel.facts` — a starting per-jurisdiction compliance
catalog (VNM/BGD/USA/IND/GBR/DEU) with official spec-basis citations
for fair-labor / labeling requirements. Additive, honest coverage
reporting; not a claim of global coverage.

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
clojure -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
clojure -M:dev:test

# Run the demo
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Status

`:implemented` — minimal but real actor stack:
`operation.cljc`/`governor.cljc`/`store.cljc`/`advisor.cljc`/
`registry.cljc`/`phase.cljc`/`sim.cljc` + `deps.edn` complete the
module set; tests green, demo runnable, langgraph-clj integration
verified. Pattern craft engine is **not** in this wave.

## License

AGPL-3.0-or-later
