package com.plantstorage.decisiontree;

import java.util.*;

/** A rule-based decision tree expanded one node at a time (not a trained ML model).
 * Each answer creates a smaller candidate branch; unknown answers skip a question.
 * Missing trait data is a wildcard, never evidence for excluding a plant.
 */
public final class PlantDecisionTree {
    public record Option(String id, String label) {}
    public record Question(String id, String text, String help, List<Option> options) {}
    public record Plant(String id, String name, String originalName, String note,
                        String source, Map<String, Set<String>> traits) {}
    public record Answer(String questionId, String optionId) {}
    public record Result(Question question, List<Plant> candidates, List<Answer> answers,
                         boolean finished, String message) {}

    private static Question q(String id, String text, String help, String... choices) {
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < choices.length; i += 2)
            options.add(new Option(choices[i], choices[i + 1]));
        options.add(new Option("unknown", "Not sure — skip this question"));
        return new Question(id, text, help, List.copyOf(options));
    }

    private static final Question FOLIAGE = q("foliage", "Can you see living leaves or fern fronds?",
            "Use developed leaves from the same plant. If it is dormant or has been cut back, return when foliage is visible.",
            "yes", "Yes, foliage is visible", "no", "No, it is dormant or has no visible foliage");
    private static final List<Question> LEAF_QUESTIONS = List.of(
        q("habit", "How does the plant grow?", "Choose its normal growth, if known. A young or recently pruned plant may be difficult to judge.",
            "fern", "Fern: fronds divided along a central stalk", "climber", "Climbing or scrambling over a support",
            "shrub", "Free-standing shrub with persistent woody branches", "herb", "Soft seasonal stems or leaves growing from the base"),
        q("leaf", "What is the structure of a complete leaf?", "Follow a leaf stalk to the main stem. Leaflets are separate blades on that one stalk; lobes remain joined. Ignore flower petals.",
            "frond", "A fern frond", "compound", "Separate leaflets share one leaf stalk",
            "lobed", "One blade, deeply cut into finger-like lobes", "simple", "One blade, not deeply lobed"),
        q("arrangement", "How do the leaves attach to the main stem?", "Look at several points on a leafy stem, not at leaflets. If only a ground-level rosette is visible, choose that option.",
            "opposite", "Pairs or whorls at the same point", "alternate", "One at a time, at different heights",
            "basal", "Only a cluster or rosette near the ground"),
        q("edge", "What do the leaf edges look like?", "Inspect a developed leaf, or a leaflet on a compound leaf. Small teeth are different from deep finger-like lobes.",
            "smooth", "Smooth edges", "toothed", "Small teeth or rounded scallops", "lobed", "Deeply divided or lobed edges"),
        q("surface", "What can you see on the leaf surface?", "Look closely without tasting or crushing the plant. Tiny hairs can be difficult to see; skip if uncertain.",
            "smooth", "No obvious hairs", "hairy", "Soft-looking fine hairs", "bristly", "Conspicuous stiff bristles"),
        q("fernDivision", "Are the side divisions of the frond divided again?", "Compare one side of the central stalk. This question only helps distinguish the two ferns on your list.",
            "twice", "Yes: each side division has many smaller divisions", "once", "No: broad lobes remain undivided"),
        q("leaflets", "How many leaflets form a typical complete leaf?", "Count several fully developed leaves. Use Not sure if the count varies or the divisions are difficult to follow.",
            "three", "Three", "many", "More than three")
    );
    private static final Question FLOWERS = q("flowers", "Are flowers available for an optional check?",
            "Leaves may leave several possible matches. You can finish without flowers.",
            "yes", "Yes, use flower features", "no", "No flowers present — show possible matches");
    private static final Question FLOWER_SHAPE = q("flowerShape", "Which description best matches the flowers?",
            "Choose the whole flower or flower head, not just its colour. Cultivars can differ. If none fits, choose Not sure.",
            "open", "Open cups, stars or saucers with separate petal-like parts",
            "tube", "Tubes, trumpets, bells or two-lipped flowers",
            "pincushion", "Tiny flowers in a pincushion surrounded by papery bracts",
            "orchid", "Irregular orchid flowers with a distinct projecting lip");

    private static Plant p(String id, String name, String original, String note, String source, String... pairs) {
        Map<String, Set<String>> traits = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            traits.put(pairs[i], Set.of(pairs[i + 1].split(",")));
        return new Plant(id, name, original, note, source, Collections.unmodifiableMap(traits));
    }
    private static String nc(String slug) { return "https://plants.ces.ncsu.edu/plants/" + slug + "/"; }
    private static final String GERANIUM = "https://www.rhs.org.uk/plants/geranium";
    private static final String SALVIA = "https://ucanr.edu/node/116238/printable/print";
    private static final String CAMPSIS = "https://www.rhs.org.uk/plants/campsis/growing-guide";

    // Conservative profiles: omitted attributes remain unknown. Do not invent a
    // distinguishing trait merely to force 32 unique terminal leaves.
    public static final List<Plant> PLANTS = List.of(
        p("g-sanguineum", "Geranium sanguineum", "Geranium sanguineum",
          "Deeply divided foliage. Compare the complete leaf outline and flowers with the other cranesbills; leaf divisions alone do not confirm the species.", nc("geranium-sanguineum"),
          "habit","herb","leaf","lobed","edge","lobed","flowerShape","open"),
        p("g-pratense", "Geranium pratense", "Geranium pratense",
          "An upright cranesbill with deeply cut leaves. Height varies; confirm against the other Geranium entries using the source and the client's plant label.", "https://gobotany.nativeplanttrust.org/species/geranium/pratense/",
          "habit","herb","leaf","lobed","edge","lobed","flowerShape","open"),
        p("g-macrorrhizum", "Geranium macrorrhizum", "Geranium macrorrhizum",
          "Spreading rhizomes and divided foliage are useful checks. Aromatic foliage is described in the source, but this guide does not require handling or crushing leaves.", "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:373281-1/general-information",
          "habit","herb","leaf","lobed","edge","lobed","flowerShape","open"),
        p("g-phaeum", "Geranium phaeum", "Geranium phaeum",
          "Some forms have dark leaf markings, but their absence does not exclude it. Compare flowers and a verified label to separate the cranesbills.", GERANIUM,
          "habit","herb","leaf","lobed","edge","lobed","flowerShape","open"),
        p("s-greggii", "Salvia greggii", "Salvia greggii",
          "Typically smaller, smooth-edged leaves than S. microphylla. Hybrids and cultivars overlap, so treat this as a possible match.", SALVIA,
          "habit","shrub,herb","leaf","simple","arrangement","opposite,basal","edge","smooth","flowerShape","tube"),
        p("s-microphylla", "Salvia microphylla", "Salvia microphylla",
          "Typically has more visibly veined, toothed leaves than S. greggii. Hybrid salvias can look intermediate; verify the nursery label.", SALVIA,
          "habit","shrub,herb","leaf","simple","arrangement","opposite,basal","edge","toothed","flowerShape","tube"),
        p("tricyrtis", "Tricyrtis hirta", "Tricyrtis hirta",
          "Hairy, pointed leaves clasp the stem. Spotted, open star-like flowers help distinguish it from the true orchid Epipactis.", nc("tricyrtis-hirta"),
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","smooth","surface","hairy","flowerShape","open"),
        p("o-tetragona", "Oenothera tetragona", "Oenothera tetragona",
          "Narrow leaves and yellow cup-like flowers. Compare a flowering specimen with the other evening primrose; this key does not force a leaf-only separation.", nc("oenothera-tetragona"),
          "habit","herb","leaf","simple","arrangement","alternate,basal","flowerShape","open"),
        p("o-odorata", "Oenothera odorata", "Oenothera odorata",
          "Label needs clarification: O. odorata has been used with different botanical authors. Confirm the full nursery name before adding finer species rules; only broad evening-primrose traits are used here.", "https://powo.science.kew.org/taxon/324893-2",
          "habit","herb","leaf","simple,lobed","flowerShape","open"),
        p("gaura", "Oenothera lindheimeri", "Gaura lindheimerii",
          "Often sold as Gaura lindheimeri. Slender, wiry stems carry small white or pink flowers; narrow leaves alone overlap with other herbaceous entries.", nc("oenothera-lindheimeri"),
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","smooth,toothed","surface","hairy,smooth","flowerShape","open"),
        p("n-faassenii", "Nepeta × faassenii", "Nepeta × faassenii",
          "Catmint with paired grey-green foliage and two-lipped flowers. Kept alongside N. racemosa: these basic observations do not reliably separate them. Check provenance or a specialist key.", nc("nepeta-x-faassenii"),
          "habit","herb","leaf","simple","arrangement","opposite,basal","edge","toothed","surface","hairy","flowerShape","tube"),
        p("n-racemosa", "Nepeta racemosa", "Nepeta racemosa",
          "Paired catmint leaves and two-lipped flowers. This tree deliberately retains both catmints; cultivar naming and appearance overlap.", "https://www.rhs.org.uk/plants/68135/nepeta-racemosa/details",
          "habit","herb","leaf","simple","arrangement","opposite,basal","edge","toothed","surface","hairy","flowerShape","tube"),
        p("c-radicans", "Campsis radicans", "Campsis radicans",
          "A vigorous trumpet vine. This guide keeps both Campsis entries together; examine a detailed botanical key and plant provenance before choosing the species or hybrid.", CAMPSIS,
          "habit","climber","leaf","compound","arrangement","opposite,basal","edge","toothed","leaflets","many","flowerShape","tube"),
        p("c-tag", "Campsis × tagliabuana", "Campsis tagliabuana",
          "A hybrid trumpet vine. General climbing habit and divided leaves do not reliably distinguish it from C. radicans.", CAMPSIS,
          "habit","climber","leaf","compound","arrangement","opposite,basal","edge","toothed","leaflets","many","flowerShape","tube"),
        p("digitalis", "Digitalis purpurea", "Digitalis purpurea",
          "A basal rosette and hairy leaves are useful clues. A tall spike of spotted tubular flowers is a stronger check when available.", nc("digitalis-purpurea"),
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","toothed","surface","hairy","flowerShape","tube"),
        p("phygelius", "Phygelius capensis", "Phygelius capensis",
          "Opposite, toothed leaves on a shrub or seasonal regrowth. Nodding tubular flowers on tall spikes help distinguish it from fuchsia and salvia.", "https://pza.sanbi.org/phygelius-capensis",
          "habit","shrub,herb","leaf","simple","arrangement","opposite,basal","edge","toothed","flowerShape","tube"),
        p("astrantia", "Astrantia major", "Astrantia major",
          "Lobed leaves can resemble cranesbills. Pincushion flower heads surrounded by papery bracts are a useful additional check; retain the group when flowerless.", "https://www.rhs.org.uk/plants/1870/astrantia-major/details",
          "habit","herb","leaf","lobed","edge","lobed","flowerShape","pincushion"),
        p("fuchsia", "Fuchsia magellanica", "Fuchsia magellanica",
          "Toothed leaves in opposite pairs or whorls, often on slender reddish stems. Pendant flowers help separate it from Phygelius. Cold weather may remove woody top growth.", nc("fuchsia-magellanica"),
          "habit","shrub,herb","leaf","simple","arrangement","opposite,basal","edge","toothed","surface","smooth","flowerShape","tube"),
        p("t-sylvestris", "Tulipa sylvestris", "Tulipa sylvestris",
          "A bulbous plant with simple narrow leaves. Yellow pointed spring flowers support this match; keep both tulips when only foliage is available.", "https://www.rhs.org.uk/plants/18545/tulipa-sylvestris-15/details",
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","smooth","surface","smooth","flowerShape","open"),
        p("t-tarda", "Tulipa tarda", "Tulipa tarda",
          "A small tulip with glossy narrow leaves. White-tipped yellow star-like flowers are useful for comparison. Retained under the client's horticultural name.", "https://www.rhs.org.uk/plants/18549/tulipa-tarda-15/details",
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","smooth","surface","smooth","flowerShape","open"),
        p("cynoglossum", "Cynoglossum amabile", "Cynoglossum amabile",
          "Hairy, lance-shaped leaves, with basal leaves larger than stem leaves. Small forget-me-not-like flowers help distinguish it from other hairy plants.", nc("cynoglossum-amabile"),
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","smooth","surface","hairy","flowerShape","open,tube"),
        p("epipactis", "Epipactis gigantea", "Epipactis gigantea",
          "Leafy stems with sheathing leaves. A true orchid flower with a distinct lip is needed for a stronger identification; compare with Tricyrtis when flowerless.", "https://www.fs.usda.gov/Internet/FSE_DOCUMENTS/stelprdb5206982.pdf",
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","smooth","flowerShape","orchid"),
        p("dryopteris", "Dryopteris filix-mas", "Dryopteris Filip-mas",
          "Frond side divisions are subdivided again into small segments. This is a possible match among these two ferns, not a key to all fern species.", nc("dryopteris-filix-mas"),
          "habit","fern","leaf","frond","fernDivision","twice"),
        p("polypodium", "Polypodium vulgare", "Polypodium vulgäre",
          "Leathery fronds have a row of broad, undivided lobes along each side. Other polypodies outside this list can look similar.", nc("polypodium-vulgare"),
          "habit","fern","leaf","frond","fernDivision","once"),
        p("rosa", "Rosa sp.", "Rosa spec.",
          "Rose identified only to genus: no rose species was supplied. Woody stems and compound toothed leaves are useful clues; some roses have few or no prickles.", nc("rosa"),
          "habit","shrub,climber","leaf","compound","edge","toothed","flowerShape","open"),
        p("echium", "Echium vulgare", "Echium vulgare",
          "Conspicuous bristles on narrow leaves and stems. Blue funnel-like flowers with projecting stamens strengthen the identification.", nc("echium-vulgare"),
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","smooth","surface","bristly","flowerShape","tube"),
        p("clematis-a", "Clematis armandii", "Clematis armandii",
          "Typically three glossy, leathery, smooth-edged leaflets. Compare the whole leaf with other climbers rather than mistaking leaflets for separate leaves.", "https://www.treesandshrubsonline.org/articles/clematis/clematis-armandii/",
          "habit","climber","leaf","compound","edge","smooth","leaflets","three","surface","smooth","flowerShape","open"),
        p("clematis-v", "Clematis viticella", "Clematis viticella",
          "A climber with divided leaves. Variable garden forms make flowers and provenance useful; a purple flower alone is insufficient.", nc("clematis-viticella"),
          "habit","climber","leaf","compound","surface","smooth","flowerShape","tube,open"),
        p("p-lactiflora", "Paeonia lactiflora", "Paeonia lactiflora",
          "Divided peony foliage. Leaf-margin details and side flower buds can help a specialist, but this simple key retains both peonies. Verify cultivar or provenance.", "https://www.peonysociety.org/species/herbaceous/albiflorae/lactiflora/",
          "habit","herb","leaf","compound,lobed","flowerShape","open"),
        p("p-officinalis", "Paeonia officinalis", "Paeonia officinalis",
          "Divided peony foliage. Leaf segmentation and hairiness vary; both peonies remain possible without a closer botanical comparison.", "https://www.peonysociety.org/species/herbaceous/paeonia/officinalis/",
          "habit","herb","leaf","compound,lobed","flowerShape","open"),
        p("campanula", "Campanula glomerata", "Campanula glomerata",
          "Simple leaves, often with toothed edges; lower and upper leaves differ. Dense clusters of bell-shaped flowers provide a stronger check.", nc("campanula-glomerata"),
          "habit","herb","leaf","simple","arrangement","alternate,basal","edge","toothed","flowerShape","tube"),
        p("lonicera", "Lonicera henryi", "Lonicera henry",
          "A twining honeysuckle with simple opposite leaves. Some references include this name under L. acuminata. Confirm the client's nursery label.", nc("lonicera-acuminata"),
          "habit","climber","leaf","simple","arrangement","opposite,basal","edge","smooth","flowerShape","tube")
    );

    private static boolean matches(Plant plant, String question, String answer) {
        Set<String> values = plant.traits().get(question);
        return values == null || values.contains(answer);
    }

    /** Select a useful unused question. Stable order keeps paths reproducible. */
    private Question next(List<Plant> candidates, Map<String, String> answers) {
        if (!answers.containsKey("foliage")) return FOLIAGE;
        if ("no".equals(answers.get("foliage")) || candidates.size() <= 1) return null;
        for (Question question : LEAF_QUESTIONS) {
            if (answers.containsKey(question.id())) continue;
            if (question.id().equals("fernDivision") && candidates.stream().anyMatch(p -> !p.traits().containsKey("fernDivision"))) continue;
            if (question.id().equals("leaflets") && candidates.stream().anyMatch(p -> !p.traits().getOrDefault("leaf", Set.of()).equals(Set.of("compound")))) continue;
            // Only ask when at least two observed answers retain different sets.
            Set<List<String>> branches = new HashSet<>();
            for (Option option : question.options()) {
                if (option.id().equals("unknown")) continue;
                List<String> ids = candidates.stream().filter(p -> matches(p, question.id(), option.id())).map(Plant::id).toList();
                if (!ids.isEmpty()) branches.add(ids);
            }
            if (branches.size() > 1) return question;
        }
        boolean allFerns = candidates.stream().allMatch(p -> p.traits().getOrDefault("habit", Set.of()).equals(Set.of("fern")));
        if (allFerns) return null;
        if (!answers.containsKey("flowers")) return FLOWERS;
        if ("yes".equals(answers.get("flowers")) && !answers.containsKey("flowerShape")) return FLOWER_SHAPE;
        return null;
    }

    public Result evaluate(List<Answer> path) {
        if (path == null || path.size() > 12) throw new IllegalArgumentException("Invalid answer history");
        List<Plant> candidates = PLANTS;
        Map<String, String> answers = new LinkedHashMap<>();
        for (Answer answer : path) {
            Question question = next(candidates, answers);
            if (answer == null || question == null || !question.id().equals(answer.questionId()) ||
                question.options().stream().noneMatch(o -> o.id().equals(answer.optionId())))
                throw new IllegalArgumentException("Invalid question or answer. Restart the identification.");
            answers.put(question.id(), answer.optionId());
            if (!Set.of("foliage", "flowers").contains(question.id()) && !answer.optionId().equals("unknown"))
                candidates = candidates.stream().filter(p -> matches(p, question.id(), answer.optionId())).toList();
        }
        Question next = next(candidates, answers);
        String message = next != null ? "Answer using the plant in front of you; skip features you cannot judge."
            : candidates.isEmpty() ? "No listed plant matches these answers. Go Back to check an observation, or Restart. The plant may be outside this list."
            : "no".equals(answers.get("foliage")) ? "Not enough visible foliage to identify this plant. All 32 entries remain possible; return when leaves emerge."
            : candidates.size() == 1 ? "One possible match within your list. Compare the source before choosing; this is not a confirmed identification."
            : "These plants remain possible. Compare the notes and sources; flowers, a nursery label or closer botanical inspection may be needed.";
        return new Result(next, List.copyOf(candidates), List.copyOf(path), next == null, message);
    }
}
