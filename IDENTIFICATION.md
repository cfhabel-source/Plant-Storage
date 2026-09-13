# Plant identification feature

## Use

Restart the app from its project terminal with `./gradlew.bat --no-daemon run`, then refresh the browser with Ctrl+F5. Open **Identify a plant** above the plant form.

Answer one question at a time. **Not sure** skips a question without removing candidates. **Back** restores the earlier candidates; **Restart** begins with all 32 entries. Vegetative questions come first. If more than one candidate remains, the optional flower check can refine the result. **No flowers present** finishes with the remaining possibilities.

**Use this plant** copies a candidate's name to the existing species field. It does not submit the form or write to Google Drive. An existing different species requires confirmation before replacement. With no living foliage, the feature returns all entries and does not offer a selection button.

## Files

- `PlantDecisionTree.java`: immutable plant profiles, questions, branch selection, answer validation and evaluation.
- `IdentificationServlet.java`: `GET /identify/?answers=[...]`; returns the current question and candidates as JSON. Malformed or out-of-order answers return HTTP 400.
- `identify.js` and `identify.css`: independent UI, error/retry handling, Back/Restart and form integration.
- `Main.java`: registers the new servlet.
- `index.html`: loads the new assets and adds a collapsible section.
- `PlantDecisionTreeTest.java`: automated behaviour checks.
- `build.gradle.kts`: adds `testIdentification` and includes it in `check`; no additional dependencies.

## Algorithm and IA explanation

This is a hand-authored, rule-based decision tree, expanded lazily from trait profiles. It is not a machine-learning classifier and does not use a stored fixed binary tree. Each node consists of the remaining candidates and questions already answered. Choosing an observation follows a branch that retains compatible candidates. Missing botanical data is a wildcard. Choosing Not sure follows a skip branch.

The next unused vegetative question is selected in a fixed order only if its answers produce different nonempty candidate groups. Special fern and leaflet questions have applicability checks. When no useful vegetative question remains, flowering plants can take an optional flower branch. Traversal ends with zero, one, or multiple candidates; it never invents confidence percentages.

The endpoint reconstructs the path from the beginning on each request. This makes Back simple and keeps concurrent visitors independent: there is no shared mutable current-node variable. UI requests are cancelled when superseded, and an unsuccessful request does not commit the proposed answer history.

## Botanical scope and limitations

This is a conservative candidate guide for these 32 entries, not a validated field key for all plants. The profiles use broad traits supported by botanical and horticultural sources, with omissions and multiple values where a finer rule is unsafe. A retained candidate means it has not been excluded by these rules; it does not mean every trait has been positively verified.

Only inspect visible traits; no questions require tasting, crushing leaves, digging up roots, or damaging specimens. Foliage must be present for a useful result. Juvenile, pruned, damaged and dormant plants can be difficult to judge. Choose Not sure when features vary or are unclear.

Some groups intentionally stay ambiguous, including the two catmints, trumpet vines and peonies. Several cranesbills, tulips and other herbaceous plants also overlap. A flower shape alone is not sufficient to reliably split every species or cultivar. This feature covers all 32 names but does NOT promise 32 unique identifications. Results include source links and suggested additional observations.

Names preserved as `originalName` alongside display names:

- `Dryopteris Filip-mas` → `Dryopteris filix-mas`.
- `Polypodium vulgäre` → `Polypodium vulgare`.
- `Campsis tagliabuana` → `Campsis × tagliabuana`.
- `Gaura lindheimerii` → `Oenothera lindheimeri` (often sold as Gaura lindheimeri).
- `Lonicera henry` → `Lonicera henryi`; the linked NC State profile treats this under L. acuminata.
- `Rosa spec.` → `Rosa sp.`; only a genus-level entry, not an unspecified invented rose species.
- `Oenothera odorata` is retained provisionally. The botanical author/nursery label needs confirmation; the RHS page for O. odorata Hook. & Arn. and Kew's O. odorata Jacq. illustrate why the name alone is ambiguous. Only broad evening-primrose traits are encoded.

## Validation

Run without OAuth or a Drive connection:

```powershell
.\gradlew.bat --no-daemon testIdentification
```

Automated Java checks cover all 32 non-flowering paths, unknown-answer preservation, dormant plants, a fern split, retained catmint ambiguity, an optional Astrantia flower result, contradictory observations, Back, Restart and invalid paths. These are software consistency tests, not independent evidence of botanical identification accuracy.

The feature was also exercised in headless Edge against a real Jetty 11 / Gson servlet on an isolated local preview: fern identification; ambiguous results; no-flower and dormant routes; Back/Restart; selection into the form without a write request; narrow-screen layout; invalid HTTP requests; and retry after a network failure. No page JavaScript errors occurred. The preview does not use Google OAuth and does not alter the client's Drive data.

Full sources were compiled using Java 21 and cached dependencies. The sandboxed Java compiler reported a Windows archive-cleanup access error after producing classes; the resulting endpoint ran successfully. The Gradle task should also be run in the user's normal project terminal. No full live Drive regression was performed for this feature.

Before describing this as a validated identifier in the IA, test actual labelled specimens with the client, including non-flowering examples. Record whether the correct entry is retained, candidate count, skipped questions, mistaken observations and usability feedback. Check the questionable nursery names with the client. Expand trait rules only when a reliable source and specimen observations justify them.

## Sources

Each plant result links to its botanical/horticultural source. The profile notes are original summaries, not copied keys. Sources were consulted on 12 September 2026.

Core references include [NC State Extension Plant Toolbox](https://plants.ces.ncsu.edu/), [RHS Geranium guide](https://www.rhs.org.uk/plants/geranium), [RHS Campsis guide](https://www.rhs.org.uk/plants/campsis/growing-guide), [UC ANR's small-leaf salvia comparison](https://ucanr.edu/node/116238/printable/print), [SANBI Phygelius capensis](https://pza.sanbi.org/phygelius-capensis), [US Forest Service Epipactis assessment](https://www.fs.usda.gov/Internet/FSE_DOCUMENTS/stelprdb5206982.pdf), [Kew Oenothera odorata](https://powo.science.kew.org/taxon/324893-2), and [RHS O. odorata Hook. & Arn.](https://www.rhs.org.uk/plants/105623/oenothera-odorata-hook-arn/details).

Peony comparisons: [Paeonia lactiflora](https://www.peonysociety.org/species/herbaceous/albiflorae/lactiflora/) and [Paeonia officinalis](https://www.peonysociety.org/species/herbaceous/paeonia/officinalis/). Additional sources and the exact coded traits for all 32 entries are in `PlantDecisionTree.PLANTS`.
